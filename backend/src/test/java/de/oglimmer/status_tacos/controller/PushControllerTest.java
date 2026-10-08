/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.oglimmer.status_tacos.config.TestSecurityConfig;
import de.oglimmer.status_tacos.persistence.Tenant;
import de.oglimmer.status_tacos.persistence.User;
import de.oglimmer.status_tacos.repository.TenantRepository;
import de.oglimmer.status_tacos.repository.UserRepository;
import de.oglimmer.status_tacos.service.ApnsClient;
import de.oglimmer.status_tacos.service.UserTenantResolver;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/** The REST API the iOS app uses for push alerts. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
@TestPropertySource(properties = "monitor.apns.enabled=true")
@Transactional
class PushControllerTest {

  private static final String TOKEN = "cd".repeat(32);

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private UserRepository userRepository;

  @MockitoBean private UserTenantResolver userTenantResolver;
  @MockitoBean private ApnsClient apnsClient;

  private MockMvc mockMvc;
  private Tenant tenant;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
    tenant =
        tenantRepository.save(
            Tenant.builder().name("Prod").code("PC" + System.nanoTime() % 1_000_000).build());
    User user =
        userRepository.save(
            User.builder()
                .email("ios@example.com")
                .oidcSubject("sub-ios")
                .tenants(new HashSet<>(Set.of(tenant)))
                .build());
    when(userTenantResolver.getCurrentUser()).thenReturn(Optional.of(user));
    when(userTenantResolver.getCurrentUserTenantIds()).thenReturn(Set.of(tenant.getId()));
    when(apnsClient.send(any(), any())).thenReturn(ApnsClient.Outcome.DELIVERED);
  }

  @Test
  @WithMockUser
  void registerDeviceThenSaveTestAndDeleteAPushAlert() throws Exception {
    mockMvc
        .perform(
            put("/v1/push/devices")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"token\":\""
                        + TOKEN
                        + "\",\"environment\":\"SANDBOX\",\"deviceName\":\"iPhone\"}"))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(get("/v1/push/status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(true))
        .andExpect(jsonPath("$.devices").value(1));

    mockMvc
        .perform(
            put("/v1/push/alerts/" + tenant.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"isActive\":true,\"allMonitors\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.type").value("IOS_PUSH"))
        .andExpect(jsonPath("$.allMonitors").value(true))
        .andExpect(jsonPath("$.owner.email").value("ios@example.com"));
    mockMvc
        .perform(get("/v1/push/alerts"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].tenant.id").value(tenant.getId()));

    mockMvc.perform(post("/v1/push/alerts/" + tenant.getId() + "/test")).andExpect(status().isOk());

    mockMvc.perform(delete("/v1/push/alerts/" + tenant.getId())).andExpect(status().isNoContent());
    mockMvc.perform(delete("/v1/push/alerts/" + tenant.getId())).andExpect(status().isNotFound());

    mockMvc.perform(delete("/v1/push/devices/" + TOKEN)).andExpect(status().isNoContent());
    mockMvc.perform(get("/v1/push/status")).andExpect(jsonPath("$.devices").value(0));
  }

  @Test
  @WithMockUser
  void pausingKeepsThePushAlert() throws Exception {
    mockMvc
        .perform(
            put("/v1/push/alerts/" + tenant.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"isActive\":false,\"allMonitors\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isActive").value(false));
  }

  @Test
  @WithMockUser
  void otherTenantsAreForbidden() throws Exception {
    mockMvc
        .perform(
            put("/v1/push/alerts/999999")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"allMonitors\":true}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser
  void unknownUsersAreForbidden() throws Exception {
    when(userTenantResolver.getCurrentUser()).thenReturn(Optional.empty());

    mockMvc.perform(get("/v1/push/alerts")).andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser
  void badInputIsABadRequest() throws Exception {
    mockMvc
        .perform(
            put("/v1/push/devices")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"xyz\",\"environment\":\"PRODUCTION\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Invalid device token"));
    mockMvc
        .perform(
            put("/v1/push/alerts/" + tenant.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"allMonitors\":false,\"monitorIds\":[]}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser
  void testWithoutDevicesIsAConflict() throws Exception {
    mockMvc
        .perform(
            put("/v1/push/alerts/" + tenant.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"allMonitors\":true}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(post("/v1/push/alerts/" + tenant.getId() + "/test"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").exists());
  }
}
