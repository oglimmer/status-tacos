/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.oglimmer.status_tacos.config.TestSecurityConfig;
import de.oglimmer.status_tacos.persistence.Tenant;
import de.oglimmer.status_tacos.repository.TenantRepository;
import de.oglimmer.status_tacos.service.UserTenantResolver;
import jakarta.persistence.EntityManager;
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

/** The environment field of a monitor over the REST API, with the JSON as the apps send it. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
@Transactional
@WithMockUser(username = "testuser")
class MonitorControllerTest {

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private EntityManager entityManager;

  @MockBean private UserTenantResolver userTenantResolver;

  private MockMvc mockMvc;
  private Tenant tenant;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
    tenant =
        tenantRepository.save(
            Tenant.builder()
                .name("Env Tenant")
                .code("env-" + System.nanoTime() % 1_000_000)
                .isActive(true)
                .build());
    when(userTenantResolver.getCurrentUserTenantIds()).thenReturn(Set.of(tenant.getId()));
    when(userTenantResolver.hasAccessToTenant(anyInt())).thenReturn(true);
    entityManager.flush();
    entityManager.clear();
  }

  private String body(String environmentJson) {
    return """
        {"name":"Env","url":"https://env.example.com","tenantId":%d,"state":"ACTIVE"%s}
        """
        .formatted(
            tenant.getId(), environmentJson == null ? "" : ",\"environment\":" + environmentJson);
  }

  private int create(String environmentJson) throws Exception {
    String response =
        mockMvc
            .perform(
                post("/v1/monitors")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(environmentJson)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    // Each request has its own transaction in the app: start the next one with a fresh session
    entityManager.flush();
    entityManager.clear();
    return objectMapper.readTree(response).get("id").asInt();
  }

  @Test
  void environment_roundTrip_createUpdateRead() throws Exception {
    int id = create("\"PROD\"");
    mockMvc
        .perform(get("/v1/monitors"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].environment").value("PROD"));

    mockMvc
        .perform(
            put("/v1/monitors/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("\"STAGE\"")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.environment").value("STAGE"));

    // PUT without environment: not set, and left out of the JSON
    mockMvc
        .perform(
            put("/v1/monitors/" + id).contentType(MediaType.APPLICATION_JSON).content(body(null)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.environment").doesNotExist());
  }

  @Test
  void environment_nullOrEmpty_isNotSet() throws Exception {
    create("null");
    mockMvc.perform(get("/v1/monitors")).andExpect(jsonPath("$[0].environment").doesNotExist());
  }

  @Test
  void environment_emptyText_isNotSet() throws Exception {
    create("\"\"");
    mockMvc.perform(get("/v1/monitors")).andExpect(jsonPath("$[0].environment").doesNotExist());
  }

  @Test
  void stateChange_keepsEnvironment() throws Exception {
    int id = create("\"DEV\"");

    mockMvc
        .perform(patch("/v1/monitors/" + id + "/state").param("state", "SILENT"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("SILENT"))
        .andExpect(jsonPath("$.environment").value("DEV"));
    mockMvc
        .perform(patch("/v1/monitors/" + id + "/toggle-status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.environment").value("DEV"));
  }

  @Test
  void environment_unknownValue_is400WithMessage() throws Exception {
    for (String value : new String[] {"\"QA\"", "\"prod\"", "42"}) {
      mockMvc
          .perform(
              post("/v1/monitors").contentType(MediaType.APPLICATION_JSON).content(body(value)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error").value("invalid_request"))
          .andExpect(jsonPath("$.message").value(containsString("DEV, STAGE, PROD")));
    }
  }

  @Test
  void state_unknownValue_is400WithMessage() throws Exception {
    String json = body(null).replace("\"ACTIVE\"", "\"SLEEPING\"");
    mockMvc
        .perform(post("/v1/monitors").contentType(MediaType.APPLICATION_JSON).content(json))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(containsString("ACTIVE, SILENT, INACTIVE")));
  }
}
