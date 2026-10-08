/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.oglimmer.status_tacos.config.ApnsConfig;
import de.oglimmer.status_tacos.config.TestSecurityConfig;
import de.oglimmer.status_tacos.dto.AlertContactRequestDto;
import de.oglimmer.status_tacos.dto.AlertContactResponseDto;
import de.oglimmer.status_tacos.dto.PushAlertRequestDto;
import de.oglimmer.status_tacos.persistence.AlertContact;
import de.oglimmer.status_tacos.persistence.CheckResult;
import de.oglimmer.status_tacos.persistence.Monitor;
import de.oglimmer.status_tacos.persistence.PushDevice;
import de.oglimmer.status_tacos.persistence.Tenant;
import de.oglimmer.status_tacos.persistence.User;
import de.oglimmer.status_tacos.repository.AlertHistoryRepository;
import de.oglimmer.status_tacos.repository.CheckResultRepository;
import de.oglimmer.status_tacos.repository.MonitorRepository;
import de.oglimmer.status_tacos.repository.PushDeviceRepository;
import de.oglimmer.status_tacos.repository.TenantRepository;
import de.oglimmer.status_tacos.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/** iOS push alerts from the alert contact to the APNs call. APNs itself is a mock. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
@TestPropertySource(properties = "monitor.apns.enabled=true")
@Transactional
class PushAlertFlowTest {

  private static final String TOKEN = "ab".repeat(32);

  @MockitoBean private ApnsClient apnsClient;

  @Autowired private AlertService alertService;
  @Autowired private AlertContactService alertContactService;
  @Autowired private PushNotificationService pushNotificationService;
  @Autowired private ApnsConfig apnsConfig;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private MonitorRepository monitorRepository;
  @Autowired private CheckResultRepository checkResultRepository;
  @Autowired private PushDeviceRepository pushDeviceRepository;
  @Autowired private AlertHistoryRepository alertHistoryRepository;

  private Tenant tenant;
  private Tenant otherTenant;
  private User owner;
  private Monitor shop;
  private Monitor blog;
  private Monitor otherTenantMonitor;

  @BeforeEach
  void setUp() {
    tenant = tenantRepository.save(tenant("Prod"));
    otherTenant = tenantRepository.save(tenant("Other"));
    owner =
        userRepository.save(
            User.builder()
                .email("ada@example.com")
                .oidcSubject("sub-ada")
                .firstName("Ada")
                .lastName("Lovelace")
                .tenants(new HashSet<>(Set.of(tenant)))
                .build());
    shop = monitor("Shop", tenant);
    blog = monitor("Blog", tenant);
    otherTenantMonitor = monitor("Elsewhere", otherTenant);
    pushNotificationService.registerDevice(
        owner, TOKEN, PushDevice.Environment.PRODUCTION, "Ada's iPhone");
    when(apnsClient.send(any(), any())).thenReturn(ApnsClient.Outcome.DELIVERED);
  }

  @Test
  void downAlertGoesToTheDevicesOfTheOwner() {
    savePushAlert(true, Set.of());

    alertService.handleMonitorDown(shop, check(shop, false));

    verify(apnsClient)
        .send(
            argThat(device -> TOKEN.equals(device.getToken())),
            argThat(
                message ->
                    message.title().equals("Down: Shop")
                        && message.body().equals("HTTP 503 · Prod")
                        && message.monitorId().equals(shop.getId())));
    assertThat(alertHistoryRepository.findAll())
        .anyMatch(history -> "IOS_PUSH: ada@example.com".equals(history.getEmailSentTo()));
  }

  @Test
  void upAlertFollowsTheDownAlert() {
    savePushAlert(true, Set.of());
    alertService.handleMonitorDown(shop, check(shop, false));

    alertService.handleMonitorUp(shop, check(shop, true));

    verify(apnsClient).send(any(), argThat(message -> message.title().equals("Up again: Shop")));
  }

  @Test
  void selectedMonitorsOnly() {
    savePushAlert(false, Set.of(blog.getId()));

    alertService.handleMonitorDown(shop, check(shop, false));
    verify(apnsClient, never()).send(any(), any());

    alertService.handleMonitorDown(blog, check(blog, false));
    verify(apnsClient).send(any(), argThat(message -> message.title().equals("Down: Blog")));
  }

  @Test
  void pausedPushAlertSendsNothing() {
    PushAlertRequestDto request = request(true, Set.of());
    request.setActive(false);
    alertContactService.saveIosPushContact(owner, tenant.getId(), request, Set.of(tenant.getId()));

    alertService.handleMonitorDown(shop, check(shop, false));

    verify(apnsClient, never()).send(any(), any());
  }

  @Test
  void ownerWhoLeftTheTenantGetsNothing() {
    savePushAlert(true, Set.of());
    owner.setTenants(new HashSet<>(Set.of(otherTenant)));
    userRepository.save(owner);

    alertService.handleMonitorDown(shop, check(shop, false));

    verify(apnsClient, never()).send(any(), any());
  }

  @Test
  void nothingIsSentWhenApnsIsOff() {
    savePushAlert(true, Set.of());
    apnsConfig.setEnabled(false);
    try {
      alertService.handleMonitorDown(shop, check(shop, false));
    } finally {
      apnsConfig.setEnabled(true);
    }

    verify(apnsClient, never()).send(any(), any());
  }

  @Test
  void goneDevicesAreForgottenAndNothingIsRecorded() {
    savePushAlert(true, Set.of());
    when(apnsClient.send(any(), any())).thenReturn(ApnsClient.Outcome.DEVICE_GONE);

    alertService.handleMonitorDown(shop, check(shop, false));

    assertThat(pushDeviceRepository.findByToken(TOKEN)).isEmpty();
    assertThat(alertHistoryRepository.findAll())
        .noneMatch(history -> history.getEmailSentTo().startsWith("IOS_PUSH"));
  }

  @Test
  void aTokenMovesToTheUserWhoSignedInLast() {
    User other =
        userRepository.save(
            User.builder()
                .email("bob@example.com")
                .oidcSubject("sub-bob")
                .tenants(new HashSet<>(Set.of(tenant)))
                .build());

    pushNotificationService.registerDevice(
        other, TOKEN.toUpperCase(), PushDevice.Environment.SANDBOX, null);

    PushDevice device = pushDeviceRepository.findByToken(TOKEN).orElseThrow();
    assertThat(device.getUser().getId()).isEqualTo(other.getId());
    assertThat(device.getEnvironment()).isEqualTo(PushDevice.Environment.SANDBOX);
    assertThat(pushDeviceRepository.findByUserId(owner.getId())).isEmpty();
  }

  @Test
  void invalidTokensAreRejected() {
    assertThatThrownBy(
            () ->
                pushNotificationService.registerDevice(
                    owner, "not-hex", PushDevice.Environment.PRODUCTION, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void unregisterRemovesOnlyTheOwnDevice() {
    User other =
        userRepository.save(User.builder().email("eve@example.com").oidcSubject("sub-eve").build());

    assertThat(pushNotificationService.unregisterDevice(other, TOKEN)).isFalse();
    assertThat(pushNotificationService.unregisterDevice(owner, TOKEN)).isTrue();
    assertThat(pushDeviceRepository.findByToken(TOKEN)).isEmpty();
  }

  @Test
  void savingAgainChangesTheScopeOfTheSameAlert() {
    AlertContactResponseDto first = savePushAlert(true, Set.of());
    AlertContactResponseDto second = savePushAlert(false, Set.of(shop.getId()));

    assertThat(second.getId()).isEqualTo(first.getId());
    assertThat(second.getType()).isEqualTo(AlertContact.AlertContactType.IOS_PUSH);
    assertThat(second.isAllMonitors()).isFalse();
    assertThat(second.getMonitors()).extracting("name").containsExactly("Shop");
    assertThat(second.getOwner().email()).isEqualTo("ada@example.com");
    assertThat(second.getOwner().name()).isEqualTo("Ada Lovelace");
    assertThat(alertContactService.getIosPushContacts(owner, Set.of(tenant.getId()))).hasSize(1);
  }

  @Test
  void monitorsOfAnotherTenantAreRejected() {
    assertThatThrownBy(() -> savePushAlert(false, Set.of(otherTenantMonitor.getId())))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void deleteRemovesTheAlert() {
    savePushAlert(true, Set.of());

    assertThat(alertContactService.deleteIosPushContact(owner, tenant.getId(), tenantIds()))
        .isTrue();
    assertThat(alertContactService.deleteIosPushContact(owner, tenant.getId(), tenantIds()))
        .isFalse();
  }

  @Test
  void testNotificationGoesToTheDevices() {
    assertThat(alertContactService.sendIosPushTest(owner, tenant.getId(), tenantIds())).isFalse();
    savePushAlert(true, Set.of());

    assertThat(alertContactService.sendIosPushTest(owner, tenant.getId(), tenantIds())).isTrue();

    verify(apnsClient)
        .send(
            any(),
            argThat(
                message ->
                    message.title().equals("Test notification") && message.monitorId() == null));
  }

  @Test
  void testWithoutDevicesFails() {
    savePushAlert(true, Set.of());
    pushNotificationService.unregisterDevice(owner, TOKEN);

    assertThatThrownBy(
            () -> alertContactService.sendIosPushTest(owner, tenant.getId(), tenantIds()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void theGenericEndpointsCannotChangePushAlerts() {
    AlertContactResponseDto push = savePushAlert(true, Set.of());
    AlertContactRequestDto request = new AlertContactRequestDto();
    request.setType(AlertContact.AlertContactType.IOS_PUSH);
    request.setValue("user:1");

    assertThatThrownBy(
            () -> alertContactService.createAlertContact(request, tenant.getId(), tenantIds()))
        .hasMessageContaining("iOS app");
    assertThatThrownBy(() -> alertContactService.deleteAlertContact(push.getId(), tenantIds()))
        .hasMessageContaining("iOS app");
    assertThatThrownBy(
            () -> alertContactService.toggleAlertContactStatus(push.getId(), tenantIds()))
        .hasMessageContaining("iOS app");
    // The web app still lists them, with the owner.
    assertThat(alertContactService.getAllAlertContacts(tenantIds()))
        .anyMatch(contact -> contact.getId().equals(push.getId()) && contact.getOwner() != null);
  }

  @Test
  void otherTenantsAreRejected() {
    assertThatThrownBy(
            () ->
                alertContactService.saveIosPushContact(
                    owner, otherTenant.getId(), request(true, Set.of()), tenantIds()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private AlertContactResponseDto savePushAlert(boolean allMonitors, Set<Integer> monitorIds) {
    return alertContactService.saveIosPushContact(
        owner, tenant.getId(), request(allMonitors, monitorIds), tenantIds());
  }

  private Set<Integer> tenantIds() {
    return Set.of(tenant.getId());
  }

  private static PushAlertRequestDto request(boolean allMonitors, Set<Integer> monitorIds) {
    PushAlertRequestDto request = new PushAlertRequestDto();
    request.setAllMonitors(allMonitors);
    request.setMonitorIds(monitorIds);
    return request;
  }

  private static Tenant tenant(String name) {
    return Tenant.builder().name(name).code(name + System.nanoTime() % 1_000_000).build();
  }

  private Monitor monitor(String name, Tenant owningTenant) {
    return monitorRepository.save(
        Monitor.builder()
            .name(name)
            .url("https://" + name.toLowerCase() + ".example.com")
            .tenant(owningTenant)
            .tenantId(owningTenant.getId())
            .build());
  }

  private CheckResult check(Monitor monitor, boolean up) {
    return checkResultRepository.save(
        CheckResult.builder()
            .monitor(monitor)
            .tenantId(monitor.getTenantId())
            .isUp(up)
            .statusCode(up ? 200 : 503)
            .checkedAt(LocalDateTime.now().withNano(0))
            .build());
  }
}
