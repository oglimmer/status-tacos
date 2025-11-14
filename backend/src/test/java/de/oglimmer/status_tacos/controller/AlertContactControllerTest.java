/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.oglimmer.status_tacos.config.TestSecurityConfig;
import de.oglimmer.status_tacos.dto.AlertContactRequestDto;
import de.oglimmer.status_tacos.persistence.AlertContact;
import de.oglimmer.status_tacos.persistence.Tenant;
import de.oglimmer.status_tacos.repository.AlertContactRepository;
import de.oglimmer.status_tacos.repository.TenantRepository;
import de.oglimmer.status_tacos.service.UserTenantResolver;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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
}
