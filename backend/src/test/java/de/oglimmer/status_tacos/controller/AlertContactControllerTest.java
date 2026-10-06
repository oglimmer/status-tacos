/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.oglimmer.status_tacos.config.TestSecurityConfig;
import de.oglimmer.status_tacos.dto.AlertContactRequestDto;
import de.oglimmer.status_tacos.persistence.AlertContact;
import de.oglimmer.status_tacos.persistence.Monitor;
import de.oglimmer.status_tacos.persistence.Tenant;
import de.oglimmer.status_tacos.repository.AlertContactRepository;
import de.oglimmer.status_tacos.repository.MonitorRepository;
import de.oglimmer.status_tacos.repository.TenantRepository;
import de.oglimmer.status_tacos.service.UserTenantResolver;
import jakarta.servlet.ServletException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
@Transactional
public class AlertContactControllerTest {

  @Autowired private WebApplicationContext webApplicationContext;

  @Autowired private AlertContactRepository alertContactRepository;

  @Autowired private TenantRepository tenantRepository;

  @Autowired private MonitorRepository monitorRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private ObjectMapper objectMapper;

  @MockBean private UserTenantResolver userTenantResolver;

  private MockMvc mockMvc;
  private Tenant testTenant;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();

    // Use existing tenant from data.sql or create new one
    testTenant =
        tenantRepository
            .findById(1)
            .orElseGet(
                () -> {
                  Tenant newTenant =
                      Tenant.builder()
                          .name("Test Tenant")
                          .code("test-tenant")
                          .description("Test tenant for integration tests")
                          .isActive(true)
                          .build();
                  return tenantRepository.save(newTenant);
                });

    // Mock UserTenantResolver to return tenant IDs that include our test tenant
    when(userTenantResolver.getCurrentUserTenantIds()).thenReturn(Set.of(testTenant.getId()));
  }

  @Test
  @WithMockUser(username = "testuser")
  void testCreateAndGetAlertContactWithActiveStatus() throws Exception {
    // Create alert contact request
    AlertContactRequestDto request = new AlertContactRequestDto();
    request.setType(AlertContact.AlertContactType.EMAIL);
    request.setValue("test@example.com");
    request.setName("Test Contact");
    request.setActive(true);

    // Create alert contact
    String createResponse =
        mockMvc
            .perform(
                post("/v1/alert-contacts")
                    .param("tenantId", testTenant.getId().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isActive").value(true))
            .andExpect(jsonPath("$.value").value("test@example.com"))
            .andExpect(jsonPath("$.name").value("Test Contact"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Parse the response to get the ID
    var createdContact = objectMapper.readTree(createResponse);
    int contactId = createdContact.get("id").asInt();

    // Get the alert contact and verify isActive is true
    mockMvc
        .perform(get("/v1/alert-contacts/" + contactId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isActive").value(true))
        .andExpect(jsonPath("$.value").value("test@example.com"))
        .andExpect(jsonPath("$.name").value("Test Contact"));

    // Get all alert contacts and verify isActive is true
    mockMvc
        .perform(get("/v1/alert-contacts"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].isActive").value(true))
        .andExpect(jsonPath("$[0].value").value("test@example.com"));
  }

  @Test
  @WithMockUser(username = "testuser")
  void testCreateInactiveAlertContact() throws Exception {
    // Create inactive alert contact request
    AlertContactRequestDto request = new AlertContactRequestDto();
    request.setType(AlertContact.AlertContactType.EMAIL);
    request.setValue("inactive@example.com");
    request.setName("Inactive Contact");
    request.setActive(false);

    // Create alert contact
    mockMvc
        .perform(
            post("/v1/alert-contacts")
                .param("tenantId", testTenant.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isActive").value(false))
        .andExpect(jsonPath("$.value").value("inactive@example.com"));
  }

  @Test
  @WithMockUser(username = "testuser")
  void testToggleAlertContactStatus() throws Exception {
    // Create active alert contact
    AlertContact alertContact =
        AlertContact.builder()
            .tenant(testTenant)
            .type(AlertContact.AlertContactType.EMAIL)
            .value("toggle@example.com")
            .name("Toggle Contact")
            .isActive(true)
            .build();
    alertContact = alertContactRepository.save(alertContact);

    // Toggle status to inactive
    mockMvc
        .perform(patch("/v1/alert-contacts/" + alertContact.getId() + "/toggle-status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isActive").value(false));

    // Toggle status back to active
    mockMvc
        .perform(patch("/v1/alert-contacts/" + alertContact.getId() + "/toggle-status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isActive").value(true));
  }

  @Test
  @WithMockUser(username = "testuser")
  void testTeamsContactLimitedToSelectedMonitors() throws Exception {
    Monitor shop = saveMonitor(testTenant, "Shop");
    Monitor billing = saveMonitor(testTenant, "Billing");

    AlertContactRequestDto request = new AlertContactRequestDto();
    request.setType(AlertContact.AlertContactType.TEAMS);
    // Real workflow URLs are longer than the old 320 character limit
    request.setValue(
        "https://default0123456789abcdef0123456789ab.cd.environment.api.powerplatform.com:443"
            + "/powerautomate/automations/direct/workflows/0123456789abcdef0123456789abcdef"
            + "/triggers/manual/paths/invoke?api-version=1&sp=%2Ftriggers%2Fmanual%2Frun"
            + "&sv=1.0&sig=AbCdEfGhIjKlMnOpQrStUvWxYz0123456789abcdefgAbCdEfGhIjKlMnOpQr");
    request.setName("Ops channel");
    request.setAllMonitors(false);
    request.setMonitorIds(Set.of(shop.getId()));

    mockMvc
        .perform(
            post("/v1/alert-contacts")
                .param("tenantId", testTenant.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.type").value("TEAMS"))
        .andExpect(jsonPath("$.allMonitors").value(false))
        .andExpect(jsonPath("$.monitors.length()").value(1))
        .andExpect(jsonPath("$.monitors[0].id").value(shop.getId()))
        .andExpect(jsonPath("$.monitors[0].name").value("Shop"));

    AlertContact everyMonitor =
        alertContactRepository.save(
            AlertContact.builder()
                .tenant(testTenant)
                .type(AlertContact.AlertContactType.EMAIL)
                .value("all@example.com")
                .isActive(true)
                .build());

    List<String> forShop =
        alertContactRepository
            .findActiveByTenantIdForMonitor(testTenant.getId(), shop.getId())
            .stream()
            .map(AlertContact::getValue)
            .sorted()
            .toList();
    assertEquals(List.of("all@example.com", request.getValue()), forShop);

    List<AlertContact> forBilling =
        alertContactRepository.findActiveByTenantIdForMonitor(testTenant.getId(), billing.getId());
    assertEquals(
        List.of(everyMonitor.getId()), forBilling.stream().map(AlertContact::getId).toList());
  }

  @Test
  @WithMockUser(username = "testuser")
  void testScopedContactRejectsMonitorOfOtherTenant() throws Exception {
    // data.sql inserts tenant 1 with an explicit id, so H2's identity would collide with it
    jdbcTemplate.update(
        "INSERT INTO tenant (id, name, code, is_active, created_at, updated_at)"
            + " VALUES (999, 'Other', 'other', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
    Tenant otherTenant = tenantRepository.findById(999).orElseThrow();
    Monitor foreign = saveMonitor(otherTenant, "Foreign");

    AlertContactRequestDto request = new AlertContactRequestDto();
    request.setType(AlertContact.AlertContactType.EMAIL);
    request.setValue("scoped@example.com");
    request.setAllMonitors(false);
    request.setMonitorIds(Set.of(foreign.getId()));

    ServletException error =
        assertThrows(
            ServletException.class,
            () ->
                mockMvc.perform(
                    post("/v1/alert-contacts")
                        .param("tenantId", testTenant.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))));
    assertEquals(
        "All selected monitors must belong to the tenant", error.getRootCause().getMessage());
  }

  @Test
  @WithMockUser(username = "testuser")
  void testTeamsContactNeedsHttps() throws Exception {
    AlertContactRequestDto request = new AlertContactRequestDto();
    request.setType(AlertContact.AlertContactType.TEAMS);
    request.setValue("http://example.com/workflow");

    assertThrows(
        ServletException.class,
        () ->
            mockMvc.perform(
                post("/v1/alert-contacts")
                    .param("tenantId", testTenant.getId().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))));
  }

  private Monitor saveMonitor(Tenant tenant, String name) {
    return monitorRepository.save(
        Monitor.builder()
            .name(name)
            .url("https://" + name.toLowerCase() + ".example.com/health")
            .tenantId(tenant.getId())
            .build());
  }
}
