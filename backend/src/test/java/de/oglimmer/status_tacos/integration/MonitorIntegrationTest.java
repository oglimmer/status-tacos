/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.integration;

// @SpringBootTest
// @AutoConfigureWebMvc
// @ActiveProfiles("test")
// @Transactional
// class MonitorIntegrationTest {
//
//    @Autowired
//    private MockMvc mockMvc;
//
//    @Autowired
//    private MonitorRepository monitorRepository;
//
//    @Autowired
//    private ObjectMapper objectMapper;
//
//    private MonitorRequestDto testMonitorRequest;
//
//    @BeforeEach
//    void setUp() {
//        monitorRepository.deleteAll();
//
//        testMonitorRequest = MonitorRequestDto.builder()
//                .name("Test Monitor")
//                .url("https://example.com")
//                .isActive(true)
//                .build();
//    }
//
//    @Test
//    void createMonitor_withValidData_shouldReturn201() throws Exception {
//        String requestJson = objectMapper.writeValueAsString(testMonitorRequest);
//
//        MvcResult result = mockMvc.perform(post("/monitors")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(requestJson))
//                .andExpect(status().isCreated())
//                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
//                .andExpect(jsonPath("$.name").value("Test Monitor"))
//                .andExpect(jsonPath("$.url").value("https://example.com"))
//                .andExpect(jsonPath("$.isActive").value(true))
//                .andExpect(jsonPath("$.id").exists())
//                .andReturn();
//
//        String responseJson = result.getResponse().getContentAsString();
//        MonitorResponseDto response = objectMapper.readValue(responseJson,
// MonitorResponseDto.class);
//
//        assertThat(monitorRepository.findById(response.getId())).isPresent();
//    }
//
//    @Test
//    void createMonitor_withInvalidData_shouldReturn400() throws Exception {
//        MonitorRequestDto invalidRequest = MonitorRequestDto.builder()
//                .name("") // Invalid: empty name
//                .url("invalid-url") // Invalid: not a proper URL format
//                .isActive(true)
//                .build();
//
//        String requestJson = objectMapper.writeValueAsString(invalidRequest);
//
//        mockMvc.perform(post("/monitors")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(requestJson))
//                .andExpect(status().isBadRequest());
//    }
//
//    @Test
//    void createMonitor_withDuplicateUrl_shouldReturn400() throws Exception {
//        // First, create a monitor
//        String requestJson = objectMapper.writeValueAsString(testMonitorRequest);
//
//        mockMvc.perform(post("/monitors")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(requestJson))
//                .andExpect(status().isCreated());
//
//        // Try to create another monitor with the same URL
//        MonitorRequestDto duplicateRequest = MonitorRequestDto.builder()
//                .name("Duplicate Monitor")
//                .url("https://example.com") // Same URL
//                .isActive(true)
//                .build();
//
//        String duplicateRequestJson = objectMapper.writeValueAsString(duplicateRequest);
//
//        mockMvc.perform(post("/monitors")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(duplicateRequestJson))
//                .andExpect(status().isBadRequest());
//    }
//
//    @Test
//    void getMonitor_withExistingId_shouldReturn200() throws Exception {
//        Monitor savedMonitor = monitorRepository.save(Monitor.builder()
//                .name("Test Monitor")
//                .url("https://example.com")
//                .isActive(true)
//                .build());
//
//        mockMvc.perform(get("/monitors/{id}", savedMonitor.getId()))
//                .andExpect(status().isOk())
//                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
//                .andExpect(jsonPath("$.id").value(savedMonitor.getId()))
//                .andExpect(jsonPath("$.name").value("Test Monitor"));
//    }
//
//    @Test
//    void getMonitor_withNonExistingId_shouldReturn404() throws Exception {
//        mockMvc.perform(get("/monitors/{id}", 99999))
//                .andExpect(status().isNotFound());
//    }
//
//    @Test
//    void getAllMonitors_shouldReturnAllMonitors() throws Exception {
//        monitorRepository.save(Monitor.builder()
//                .name("Monitor 1")
//                .url("https://example1.com")
//                .isActive(true)
//                .build());
//
//        monitorRepository.save(Monitor.builder()
//                .name("Monitor 2")
//                .url("https://example2.com")
//                .isActive(false)
//                .build());
//
//        mockMvc.perform(get("/monitors"))
//                .andExpect(status().isOk())
//                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
//                .andExpect(jsonPath("$.length()").value(2))
//                .andExpect(jsonPath("$[0].name").value("Monitor 1"))
//                .andExpect(jsonPath("$[1].name").value("Monitor 2"));
//    }
//
//    @Test
//    void getAllMonitors_withActiveOnlyFilter_shouldReturnOnlyActiveMonitors() throws Exception {
//        monitorRepository.save(Monitor.builder()
//                .name("Active Monitor")
//                .url("https://active.com")
//                .isActive(true)
//                .build());
//
//        monitorRepository.save(Monitor.builder()
//                .name("Inactive Monitor")
//                .url("https://inactive.com")
//                .isActive(false)
//                .build());
//
//        mockMvc.perform(get("/monitors?activeOnly=true"))
//                .andExpect(status().isOk())
//                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
//                .andExpect(jsonPath("$.length()").value(1))
//                .andExpect(jsonPath("$[0].name").value("Active Monitor"))
//                .andExpect(jsonPath("$[0].isActive").value(true));
//    }
//
//    @Test
//    void updateMonitor_withValidData_shouldReturn200() throws Exception {
//        Monitor savedMonitor = monitorRepository.save(Monitor.builder()
//                .name("Original Monitor")
//                .url("https://original.com")
//                .isActive(true)
//                .build());
//
//        MonitorRequestDto updateRequest = MonitorRequestDto.builder()
//                .name("Updated Monitor")
//                .url("https://updated.com")
//                .isActive(false)
//                .build();
//
//        String requestJson = objectMapper.writeValueAsString(updateRequest);
//
//        mockMvc.perform(put("/monitors/{id}", savedMonitor.getId())
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(requestJson))
//                .andExpect(status().isOk())
//                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
//                .andExpect(jsonPath("$.name").value("Updated Monitor"))
//                .andExpect(jsonPath("$.url").value("https://updated.com"))
//                .andExpect(jsonPath("$.isActive").value(false));
//    }
//
//    @Test
//    void deleteMonitor_withExistingId_shouldReturn204() throws Exception {
//        Monitor savedMonitor = monitorRepository.save(Monitor.builder()
//                .name("Monitor to Delete")
//                .url("https://delete.com")
//                .isActive(true)
//                .build());
//
//        mockMvc.perform(delete("/monitors/{id}", savedMonitor.getId()))
//                .andExpect(status().isNoContent());
//
//        assertThat(monitorRepository.findById(savedMonitor.getId())).isEmpty();
//    }
//
//    @Test
//    void deleteMonitor_withNonExistingId_shouldReturn404() throws Exception {
//        mockMvc.perform(delete("/monitors/{id}", 99999))
//                .andExpect(status().isNotFound());
//    }
//
//    @Test
//    void toggleMonitorStatus_shouldChangeActiveStatus() throws Exception {
//        Monitor savedMonitor = monitorRepository.save(Monitor.builder()
//                .name("Monitor to Toggle")
//                .url("https://toggle.com")
//                .isActive(true)
//                .build());
//
//        mockMvc.perform(patch("/monitors/{id}/toggle-status", savedMonitor.getId()))
//                .andExpect(status().isOk())
//                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
//                .andExpect(jsonPath("$.isActive").value(false));
//
//        Monitor updatedMonitor = monitorRepository.findById(savedMonitor.getId()).orElseThrow();
//        assertThat(updatedMonitor.getIsActive()).isFalse();
//    }
//
//    @Test
//    void getMonitorStats_shouldReturnCorrectCounts() throws Exception {
//        monitorRepository.save(Monitor.builder()
//                .name("Active Monitor 1")
//                .url("https://active1.com")
//                .isActive(true)
//                .build());
//
//        monitorRepository.save(Monitor.builder()
//                .name("Active Monitor 2")
//                .url("https://active2.com")
//                .isActive(true)
//                .build());
//
//        monitorRepository.save(Monitor.builder()
//                .name("Inactive Monitor")
//                .url("https://inactive.com")
//                .isActive(false)
//                .build());
//
//        mockMvc.perform(get("/monitors/stats"))
//                .andExpect(status().isOk())
//                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
//                .andExpect(jsonPath("$.total").value(3))
//                .andExpect(jsonPath("$.active").value(2))
//                .andExpect(jsonPath("$.inactive").value(1));
//    }
// }
