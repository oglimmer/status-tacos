/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.oglimmer.status_tacos.config.TestSecurityConfig;
import de.oglimmer.status_tacos.persistence.Monitor;
import de.oglimmer.status_tacos.persistence.Tenant;
import de.oglimmer.status_tacos.repository.MonitorRepository;
import de.oglimmer.status_tacos.repository.TenantRepository;
import de.oglimmer.status_tacos.service.UptimeStatsService;
import de.oglimmer.status_tacos.service.UserTenantResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/** The public status page and its editor, with the real security config. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
@Transactional
class StatusPageControllerTest {

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private MonitorRepository monitorRepository;

  @MockitoBean private UserTenantResolver userTenantResolver;

  // Its SQL needs MariaDB. The mock gives no uptime, which is fine here.
  @MockitoBean private UptimeStatsService uptimeStatsService;

  private MockMvc mockMvc;
  private Tenant tenant;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
    tenant =
        tenantRepository.save(
            Tenant.builder().name("Acme").code("acme" + System.nanoTime() % 1_000_000).build());
    monitorRepository.save(
        Monitor.builder()
            .name("Public API")
            .url("https://secret.example/health")
            .tenantId(tenant.getId())
            .showOnPublicPage(true)
            .build());
    monitorRepository.save(
        Monitor.builder()
            .name("Hidden DB")
            .url("https://db.example")
            .tenantId(tenant.getId())
            .build());
    when(userTenantResolver.hasAccessToTenant(tenant.getId())).thenReturn(true);
  }

  private String pageUrl() {
    return "/v1/public/" + tenant.getCode() + "/status/page";
  }

  @Test
  void publicPage_needsNoSignIn_andIsSandboxed() throws Exception {
    mockMvc
        .perform(get(pageUrl()))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
        .andExpect(header().string("Content-Security-Policy", containsString("sandbox")))
        .andExpect(header().string("Content-Security-Policy", containsString("default-src 'none'")))
        .andExpect(header().doesNotExist("X-Frame-Options"))
        .andExpect(header().string("Cache-Control", containsString("max-age=30")))
        .andExpect(content().string(containsString("Public API")))
        .andExpect(content().string(not(containsString("Hidden DB"))))
        .andExpect(content().string(not(containsString("secret.example"))));
  }

  @Test
  void publicData_needsNoSignIn() throws Exception {
    mockMvc
        .perform(get("/v1/public/" + tenant.getCode() + "/status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tenantName").value("Acme"))
        .andExpect(jsonPath("$.monitors.length()").value(1))
        .andExpect(jsonPath("$.monitors[0].name").value("Public API"));
  }

  @Test
  void unknownTenant_isNotFound() throws Exception {
    mockMvc.perform(get("/v1/public/no-such-tenant/status/page")).andExpect(status().isNotFound());
    mockMvc.perform(get("/v1/public/no-such-tenant/status")).andExpect(status().isNotFound());
  }

  @Test
  void publicPath_isReadOnly() throws Exception {
    mockMvc
        .perform(post("/v1/public/" + tenant.getCode() + "/status"))
        .andExpect(status().isForbidden());
  }

  @Test
  void editor_needsSignIn() throws Exception {
    mockMvc
        .perform(get("/v1/tenants/" + tenant.getId() + "/status-page"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser
  void editor_otherTenant_isForbidden() throws Exception {
    mockMvc
        .perform(get("/v1/tenants/" + (tenant.getId() + 1000) + "/status-page"))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser
  void saveLook_changesThePublicPage_andResetRestoresIt() throws Exception {
    // Fills the cache with the default look
    mockMvc.perform(get(pageUrl())).andExpect(content().string(containsString("All systems up")));

    mockMvc
        .perform(
            put("/v1/tenants/" + tenant.getId() + "/status-page")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"htmlTemplate\":\"<h1 class=\\\"brand\\\">{{tenantName}}</h1><script>x()</script>"
                        + "{{#monitors}}<p>{{name}}: {{status}}</p>{{/monitors}}\","
                        + "\"css\":\":root { --brand: #ff6600; }\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.css").value(":root { --brand: #ff6600; }"))
        .andExpect(jsonPath("$.defaultHtmlTemplate").isNotEmpty());

    mockMvc
        .perform(get(pageUrl()))
        .andExpect(content().string(containsString("<h1 class=\"brand\">Acme</h1>")))
        .andExpect(content().string(containsString("<p>Public API: unknown</p>")))
        .andExpect(content().string(containsString("--brand: #ff6600")))
        .andExpect(content().string(not(containsString("<script>x()"))));

    mockMvc
        .perform(delete("/v1/tenants/" + tenant.getId() + "/status-page"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.htmlTemplate").doesNotExist());
    mockMvc.perform(get(pageUrl())).andExpect(content().string(containsString("All systems up")));
  }

  @Test
  @WithMockUser
  void newTenantCode_movesThePublicPage() throws Exception {
    String oldUrl = pageUrl();
    String newCode = "acme-status" + System.nanoTime() % 1_000_000;

    mockMvc
        .perform(
            put("/v1/tenants/" + tenant.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Acme\",\"code\":\"" + newCode + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(newCode));

    mockMvc.perform(get(oldUrl)).andExpect(status().isNotFound());
    mockMvc
        .perform(get("/v1/public/" + newCode + "/status/page"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Public API")));
  }

  @Test
  @WithMockUser
  void takenOrInvalidTenantCode_isRejectedWithAMessage() throws Exception {
    Tenant other =
        tenantRepository.save(
            Tenant.builder().name("Other").code("other" + System.nanoTime() % 1_000_000).build());

    mockMvc
        .perform(
            put("/v1/tenants/" + tenant.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Acme\",\"code\":\"" + other.getCode() + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(containsString("already has the code")));

    mockMvc
        .perform(
            put("/v1/tenants/" + tenant.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Acme\",\"code\":\"a/b\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(containsString("letters, numbers")));

    mockMvc.perform(get(pageUrl())).andExpect(status().isOk());
  }

  @Test
  @WithMockUser
  void invalidCss_isRejectedWithAMessage() throws Exception {
    mockMvc
        .perform(
            put("/v1/tenants/" + tenant.getId() + "/status-page")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"css\":\"</style><script>x()</script>\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(containsString("<")));
  }

  @Test
  @WithMockUser
  void preview_showsErrorsAndRemovedParts() throws Exception {
    mockMvc
        .perform(
            post("/v1/tenants/" + tenant.getId() + "/status-page/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"htmlTemplate\":\"<p onclick=\\\"x()\\\">{{tenantName}}</p>\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.html").value(containsString("<p>Acme</p>")))
        .andExpect(jsonPath("$.html").value(not(containsString("http-equiv=\"refresh\""))))
        .andExpect(jsonPath("$.removed[0]").value("onclick on <p>"));

    mockMvc
        .perform(
            post("/v1/tenants/" + tenant.getId() + "/status-page/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"htmlTemplate\":\"{{#monitors}}\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.html").doesNotExist())
        .andExpect(jsonPath("$.error").value(containsString("The template is invalid")));
  }
}
