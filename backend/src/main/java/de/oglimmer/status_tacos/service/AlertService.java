/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import de.oglimmer.status_tacos.config.EmailConfig;
import de.oglimmer.status_tacos.persistence.AlertContact;
import de.oglimmer.status_tacos.persistence.AlertHistory;
import de.oglimmer.status_tacos.persistence.CheckResult;
import de.oglimmer.status_tacos.persistence.Monitor;
import de.oglimmer.status_tacos.persistence.MonitorState;
import de.oglimmer.status_tacos.repository.AlertContactRepository;
import de.oglimmer.status_tacos.repository.AlertHistoryRepository;
import de.oglimmer.status_tacos.repository.CheckResultRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.http.*;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

  /** Length of alert_history.email_sent_to. */
  private static final int MAX_SENT_TO_LENGTH = 320;

  private final AlertHistoryRepository alertHistoryRepository;
  private final AlertContactRepository alertContactRepository;
  private final CheckResultRepository checkResultRepository;
  private final EmailConfig emailConfig;
  private final Optional<JavaMailSender> javaMailSender;
  private final TeamsNotificationService teamsNotificationService;
  private final RestTemplate restTemplate = new RestTemplate();

  @Transactional
  public void handleMonitorDown(Monitor monitor, CheckResult checkResult) {
    List<AlertContact> contacts = findDeliverableContacts(monitor);
    if (contacts.isEmpty()) {
      return;
    }

    if (isDownAlertUnresolved(monitor)) {
      log.debug(
          "DOWN alert already sent for monitor {} and no UP alert since then", monitor.getId());
      return;
    }

    int statusCode = checkResult.getStatusCode() != null ? checkResult.getStatusCode() : 0;
    TeamsAlert teamsAlert =
        monitorAlert(TeamsAlert.Kind.DOWN, monitor, checkResult, findOutageStart(checkResult));

    // Send DOWN alert to all active contacts of this monitor
    for (AlertContact contact : contacts) {
      sendAlert(monitor, contact, "down", statusCode, teamsAlert);
    }
  }

  @Transactional
  public void handleMonitorUp(Monitor monitor, CheckResult checkResult) {
    List<AlertContact> contacts = findDeliverableContacts(monitor);
    if (contacts.isEmpty()) {
      return;
    }

    // Only a DOWN alert without an UP alert after it needs an UP notification
    if (!isDownAlertUnresolved(monitor)) {
      return;
    }

    int statusCode = checkResult.getStatusCode() != null ? checkResult.getStatusCode() : 200;
    TeamsAlert teamsAlert =
        monitorAlert(TeamsAlert.Kind.UP, monitor, checkResult, findOutageStart(checkResult));

    for (AlertContact contact : contacts) {
      sendAlert(monitor, contact, "up", statusCode, teamsAlert);
    }
  }

  /**
   * Active contacts that are alerted for this monitor (all monitors of the tenant, or this monitor
   * selected) and that can be reached with the current configuration.
   */
  private List<AlertContact> findDeliverableContacts(Monitor monitor) {
    boolean hasEmailConfig = emailConfig.isEnabled() && javaMailSender.isPresent();

    List<AlertContact> contacts =
        alertContactRepository
            .findActiveByTenantIdForMonitor(monitor.getTenantId(), monitor.getId())
            .stream()
            .filter(
                contact ->
                    hasEmailConfig || contact.getType() != AlertContact.AlertContactType.EMAIL)
            .toList();

    if (contacts.isEmpty()) {
      log.debug(
          "No deliverable alert contacts for monitor {} of tenant {}",
          monitor.getId(),
          monitor.getTenantId());
    }
    return contacts;
  }

  /** true if the last DOWN alert of the monitor has no UP alert after it. */
  private boolean isDownAlertUnresolved(Monitor monitor) {
    Optional<AlertHistory> lastDownAlert =
        alertHistoryRepository.findTopByMonitorIdAndTenantIdAndAlertTypeOrderBySentAtDesc(
            monitor.getId(), monitor.getTenantId(), AlertHistory.AlertType.down);
    if (lastDownAlert.isEmpty()) {
      return false;
    }

    Optional<AlertHistory> lastUpAlert =
        alertHistoryRepository.findTopByMonitorIdAndTenantIdAndAlertTypeOrderBySentAtDesc(
            monitor.getId(), monitor.getTenantId(), AlertHistory.AlertType.up);

    return lastUpAlert
        .map(up -> !up.getSentAt().isAfter(lastDownAlert.get().getSentAt()))
        .orElse(true);
  }

  /**
   * The first failed check after the last successful check before the given check, i.e. when the
   * current (or, for an UP check, the just ended) outage started. null if there is none.
   */
  public LocalDateTime findOutageStart(CheckResult checkResult) {
    Integer monitorId = checkResult.getMonitor().getId();
    Integer tenantId = checkResult.getTenantId();
    List<CheckResult> lastUp =
        checkResultRepository.findLastUpBefore(
            monitorId, tenantId, checkResult.getCheckedAt(), checkResult.getId(), Limit.of(1));
    List<CheckResult> firstDown =
        checkResultRepository.findFirstDownAfter(
            monitorId,
            tenantId,
            lastUp.isEmpty() ? LocalDateTime.of(1970, 1, 1, 0, 0) : lastUp.get(0).getCheckedAt(),
            lastUp.isEmpty() ? 0L : lastUp.get(0).getId(),
            Limit.of(1));
    return firstDown.isEmpty() ? null : firstDown.get(0).getCheckedAt();
  }

  private TeamsAlert monitorAlert(
      TeamsAlert.Kind kind, Monitor monitor, CheckResult checkResult, LocalDateTime downSince) {
    return new TeamsAlert(
        kind,
        monitor.getName(),
        monitor.getUrl(),
        tenantName(monitor),
        checkResult.getStatusCode(),
        checkResult.getErrorMessage(),
        checkResult.getResponseTimeMs(),
        checkResult.getCheckedAt(),
        downSince,
        monitor.getAlertingThreshold(),
        null,
        null);
  }

  private void sendAlert(
      Monitor monitor,
      AlertContact contact,
      String alertType,
      int statusCode,
      TeamsAlert teamsAlert) {
    switch (contact.getType()) {
      case EMAIL -> sendEmailAlert(monitor, contact, alertType, statusCode, false);
      case HTTP -> sendHttpAlert(monitor, contact, alertType, statusCode, null, false);
      case TEAMS -> sendTeamsAlert(monitor, contact, alertType, teamsAlert, false);
    }
  }

  private void sendEmailAlert(
      Monitor monitor, AlertContact contact, String alertType, int statusCode, boolean test) {
    try {
      SimpleMailMessage message = new SimpleMailMessage();
      message.setFrom(emailConfig.getFrom());
      message.setTo(contact.getValue());

      String subject;
      String body;

      if ("down".equals(alertType)) {
        subject =
            String.format(
                "%s %s",
                emailConfig.getSubjectPrefix(),
                String.format(
                    emailConfig.getTemplate().getMonitorDown(), monitor.getName(), statusCode));
        body =
            String.format(
                "Monitor '%s' is currently DOWN.\n\nURL: %s\nStatus Code: %d\nTime: %s",
                monitor.getName(), monitor.getUrl(), statusCode, LocalDateTime.now());
      } else if ("test".equals(alertType)) {
        subject = String.format("%s Test Notification", emailConfig.getSubjectPrefix());
        body =
            String.format(
                "This is a test notification for alert contact '%s'.\n\nMonitor: %s\nURL: %s\nTenant: %s\nTime: %s",
                contact.getName() != null ? contact.getName() : "Unnamed Contact",
                monitor.getName(),
                monitor.getUrl(),
                contact.getTenant().getName(),
                LocalDateTime.now());
      } else {
        subject =
            String.format(
                "%s %s",
                emailConfig.getSubjectPrefix(),
                String.format(emailConfig.getTemplate().getMonitorUp(), monitor.getName()));
        body =
            String.format(
                "Monitor '%s' is now UP again.\n\nURL: %s\nTime: %s",
                monitor.getName(), monitor.getUrl(), LocalDateTime.now());
      }

      message.setSubject(subject);
      message.setText(body);

      javaMailSender.get().send(message);

      // Don't record test alerts in alert history
      if (!test) {
        recordAlert(monitor, contact, alertType, contact.getValue());
      }

      log.info(
          "Sent {} email alert for monitor {} to {} ({})",
          alertType,
          monitor.getId(),
          contact.getValue(),
          contact.getName() != null ? contact.getName() : "unnamed contact");

    } catch (Exception e) {
      log.error(
          "Failed to send {} email alert for monitor {} to {} ({}): {}",
          alertType,
          monitor.getId(),
          contact.getValue(),
          contact.getName() != null ? contact.getName() : "unnamed contact",
          e.getMessage(),
          e);
    }
  }

  private void sendHttpAlert(
      Monitor monitor,
      AlertContact contact,
      String alertType,
      int statusCode,
      String responseBody,
      boolean test) {
    try {
      String url =
          substituteVariables(contact.getValue(), monitor, alertType, statusCode, responseBody);
      String method =
          contact.getHttpMethod() != null ? contact.getHttpMethod().toUpperCase() : "GET";

      HttpHeaders headers = new HttpHeaders();
      Map<String, String> customHeaders = contact.getHttpHeadersMap();
      for (Map.Entry<String, String> header : customHeaders.entrySet()) {
        headers.add(
            header.getKey(),
            substituteVariables(header.getValue(), monitor, alertType, statusCode, responseBody));
      }

      HttpEntity<String> entity;
      if ("POST".equals(method) && contact.getHttpBody() != null) {
        String body =
            substituteVariables(
                contact.getHttpBody(), monitor, alertType, statusCode, responseBody);

        // Set content type based on configuration, default to JSON
        String contentType = contact.getHttpContentType();
        if ("text/plain".equals(contentType)) {
          headers.setContentType(MediaType.TEXT_PLAIN);
        } else {
          headers.setContentType(MediaType.APPLICATION_JSON);
        }

        entity = new HttpEntity<>(body, headers);
      } else {
        entity = new HttpEntity<>(headers);
      }

      ResponseEntity<String> response;
      if ("POST".equals(method)) {
        response = restTemplate.postForEntity(url, entity, String.class);
      } else {
        response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
      }

      // Don't record test alerts in alert history
      if (!test) {
        recordAlert(monitor, contact, alertType, url);
      }

      log.info(
          "Sent {} HTTP {} alert for monitor {} to {} ({}) - Response: {}",
          alertType,
          method,
          monitor.getId(),
          url,
          contact.getName() != null ? contact.getName() : "unnamed contact",
          response.getStatusCode());

    } catch (RestClientException e) {
      log.error(
          "Failed to send {} HTTP alert for monitor {} to {} ({}): {}",
          alertType,
          monitor.getId(),
          contact.getValue(),
          contact.getName() != null ? contact.getName() : "unnamed contact",
          e.getMessage(),
          e);
    } catch (Exception e) {
      log.error(
          "Unexpected error sending {} HTTP alert for monitor {} to {} ({}): {}",
          alertType,
          monitor.getId(),
          contact.getValue(),
          contact.getName() != null ? contact.getName() : "unnamed contact",
          e.getMessage(),
          e);
    }
  }

  private void sendTeamsAlert(
      Monitor monitor,
      AlertContact contact,
      String alertType,
      TeamsAlert teamsAlert,
      boolean test) {
    try {
      teamsNotificationService.send(contact.getValue(), teamsAlert);

      // Don't record test alerts in alert history
      if (!test) {
        recordAlert(monitor, contact, alertType, "TEAMS: " + contactLabel(contact));
      }

      log.info(
          "Sent {} Teams alert for monitor {} to {}",
          alertType,
          monitor.getId(),
          contactLabel(contact));

    } catch (RuntimeException e) {
      log.error(
          "Failed to send {} Teams alert for monitor {} to {}: {}",
          alertType,
          monitor.getId(),
          contactLabel(contact),
          e.getMessage(),
          e);
      // A test must tell the user that Teams did not accept the card
      if (test) {
        throw new IllegalStateException("Teams did not accept the notification: " + e.getMessage());
      }
    }
  }

  private String contactLabel(AlertContact contact) {
    return contact.getName() != null && !contact.getName().isBlank()
        ? contact.getName()
        : "unnamed contact #" + contact.getId();
  }

  private String tenantName(Monitor monitor) {
    return monitor.getTenant() != null ? monitor.getTenant().getName() : null;
  }

  private String substituteVariables(
      String template, Monitor monitor, String alertType, int statusCode, String responseBody) {
    if (template == null) {
      return null;
    }

    return template
        .replace("{{MONITOR_NAME}}", monitor.getName() != null ? monitor.getName() : "")
        .replace("{{MONITOR_URL}}", monitor.getUrl() != null ? monitor.getUrl() : "")
        .replace(
            "{{TENANT_NAME}}",
            monitor.getTenant() != null && monitor.getTenant().getName() != null
                ? monitor.getTenant().getName()
                : "")
        .replace("{{STATUS_CODE}}", String.valueOf(statusCode))
        .replace("{{RESPONSE_BODY}}", responseBody != null ? responseBody : "")
        .replace("{{ALERT_TYPE}}", alertType != null ? alertType : "")
        .replace("{{TIMESTAMP}}", LocalDateTime.now().toString());
  }

  private void recordAlert(Monitor monitor, AlertContact contact, String alertType, String sentTo) {
    AlertHistory alertHistory = new AlertHistory();
    alertHistory.setMonitor(monitor);
    alertHistory.setTenantId(monitor.getTenantId());
    alertHistory.setAlertType(
        "down".equals(alertType) ? AlertHistory.AlertType.down : AlertHistory.AlertType.up);
    alertHistory.setEmailSentTo(
        sentTo.length() <= MAX_SENT_TO_LENGTH ? sentTo : sentTo.substring(0, MAX_SENT_TO_LENGTH));
    alertHistory.setSentAt(LocalDateTime.now());
    alertHistoryRepository.save(alertHistory);
  }

  public void sendTestNotification(AlertContact contact) {
    boolean hasEmailConfig = emailConfig.isEnabled() && javaMailSender.isPresent();

    if (contact.getType() == AlertContact.AlertContactType.EMAIL) {
      if (!hasEmailConfig) {
        throw new IllegalStateException("Email configuration is not enabled");
      }
    }

    // Create a test monitor record with the contact's tenant
    Monitor testMonitor = createTestMonitor(contact);

    if (contact.getType() == AlertContact.AlertContactType.EMAIL) {
      sendEmailAlert(testMonitor, contact, AlertHistory.AlertType.down.name(), 200, true);
    } else if (contact.getType() == AlertContact.AlertContactType.HTTP) {
      sendHttpAlert(
          testMonitor,
          contact,
          AlertHistory.AlertType.down.name(),
          200,
          "Test notification response body",
          true);
    } else if (contact.getType() == AlertContact.AlertContactType.TEAMS) {
      sendTeamsAlert(testMonitor, contact, "test", testTeamsAlert(contact), true);
    } else {
      throw new IllegalArgumentException("Unsupported contact type: " + contact.getType());
    }

    log.info(
        "Sent test notification using production alert methods to {} ({})",
        contact.getValue(),
        contact.getName() != null ? contact.getName() : "unnamed contact");
  }

  private TeamsAlert testTeamsAlert(AlertContact contact) {
    String scope;
    if (contact.isAllMonitors()) {
      scope = "All monitors of the tenant";
    } else {
      List<String> names = contact.getMonitors().stream().map(Monitor::getName).sorted().toList();
      scope =
          names.isEmpty()
              ? "No monitor selected - this contact gets no alerts"
              : names.size() + " selected: " + String.join(", ", names);
    }
    return new TeamsAlert(
        TeamsAlert.Kind.TEST,
        null,
        null,
        contact.getTenant().getName(),
        null,
        null,
        null,
        LocalDateTime.now(),
        null,
        null,
        contactLabel(contact),
        scope);
  }

  private Monitor createTestMonitor(AlertContact contact) {
    Monitor testMonitor = new Monitor();
    testMonitor.setId(-1); // Use negative ID to indicate test monitor
    testMonitor.setName("Test Monitor - " + contact.getName());
    testMonitor.setUrl("https://example.com/test-endpoint");
    testMonitor.setTenant(contact.getTenant());
    testMonitor.setTenantId(contact.getTenantId());
    testMonitor.setState(MonitorState.ACTIVE);
    return testMonitor;
  }
}
