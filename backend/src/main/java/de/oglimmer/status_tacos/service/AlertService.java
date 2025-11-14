/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import de.oglimmer.status_tacos.config.EmailConfig;
import de.oglimmer.status_tacos.persistence.AlertContact;
import de.oglimmer.status_tacos.persistence.AlertHistory;
import de.oglimmer.status_tacos.persistence.Monitor;
import de.oglimmer.status_tacos.persistence.MonitorState;
import de.oglimmer.status_tacos.repository.AlertContactRepository;
import de.oglimmer.status_tacos.repository.AlertHistoryRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

  private final AlertHistoryRepository alertHistoryRepository;
  private final AlertContactRepository alertContactRepository;
  private final EmailConfig emailConfig;
  private final Optional<JavaMailSender> javaMailSender;
  private final RestTemplate restTemplate = new RestTemplate();

  @Transactional
  public void handleMonitorDown(Monitor monitor, int statusCode) {
    List<AlertContact> allContacts =
        alertContactRepository.findByTenantIdAndIsActiveTrue(monitor.getTenantId());

    List<AlertContact> emailContacts =
        allContacts.stream()
            .filter(contact -> contact.getType() == AlertContact.AlertContactType.EMAIL)
            .toList();

    List<AlertContact> httpContacts =
        allContacts.stream()
            .filter(contact -> contact.getType() == AlertContact.AlertContactType.HTTP)
            .toList();

    boolean hasEmailConfig = emailConfig.isEnabled() && javaMailSender.isPresent();

    if (!hasEmailConfig && httpContacts.isEmpty()) {
      log.debug("No alert methods configured for monitor {}", monitor.getId());
      return;
    }

    if (emailContacts.isEmpty() && httpContacts.isEmpty()) {
      log.debug("No active alert contacts configured for tenant {}", monitor.getTenantId());
      return;
    }

    // Check if we already sent a DOWN alert that hasn't been resolved
    Optional<AlertHistory> lastDownAlert =
        alertHistoryRepository.findTopByMonitorIdAndTenantIdAndAlertTypeOrderBySentAtDesc(
            monitor.getId(), monitor.getTenantId(), AlertHistory.AlertType.down);

    if (lastDownAlert.isPresent()) {
      // Check if there's been an UP alert since the last DOWN alert
      Optional<AlertHistory> lastUpAlert =
          alertHistoryRepository.findTopByMonitorIdAndTenantIdAndAlertTypeOrderBySentAtDesc(
              monitor.getId(), monitor.getTenantId(), AlertHistory.AlertType.up);

      boolean hasUpAlertSinceDown =
          lastUpAlert
              .map(up -> up.getSentAt().isAfter(lastDownAlert.get().getSentAt()))
              .orElse(false);

      if (!hasUpAlertSinceDown) {
        log.debug(
            "DOWN alert already sent for monitor {} and no UP alert since then", monitor.getId());
        return;
      }
    }

    // Send DOWN alert to all active contacts
    if (hasEmailConfig) {
      for (AlertContact contact : emailContacts) {
        sendEmailAlert(monitor, contact, "down", statusCode, false);
      }
    }

    for (AlertContact contact : httpContacts) {
      sendHttpAlert(monitor, contact, "down", statusCode, null, false);
    }
  }

  @Transactional
  public void handleMonitorUp(Monitor monitor) {
    List<AlertContact> allContacts =
        alertContactRepository.findByTenantIdAndIsActiveTrue(monitor.getTenantId());

    List<AlertContact> emailContacts =
        allContacts.stream()
            .filter(contact -> contact.getType() == AlertContact.AlertContactType.EMAIL)
            .toList();

    List<AlertContact> httpContacts =
        allContacts.stream()
            .filter(contact -> contact.getType() == AlertContact.AlertContactType.HTTP)
            .toList();

    boolean hasEmailConfig = emailConfig.isEnabled() && javaMailSender.isPresent();

    if (!hasEmailConfig && httpContacts.isEmpty()) {
      log.debug("No alert methods configured for monitor {}", monitor.getId());
      return;
    }

    if (emailContacts.isEmpty() && httpContacts.isEmpty()) {
      log.debug("No active alert contacts configured for tenant {}", monitor.getTenantId());
      return;
    }

    // Check if there was a previous DOWN alert that needs an UP notification
    Optional<AlertHistory> lastDownAlert =
        alertHistoryRepository.findTopByMonitorIdAndTenantIdAndAlertTypeOrderBySentAtDesc(
            monitor.getId(), monitor.getTenantId(), AlertHistory.AlertType.down);

    if (lastDownAlert.isPresent()) {
      // Check if we already sent an UP alert after the last DOWN alert
      Optional<AlertHistory> lastUpAlert =
          alertHistoryRepository.findTopByMonitorIdAndTenantIdAndAlertTypeOrderBySentAtDesc(
              monitor.getId(), monitor.getTenantId(), AlertHistory.AlertType.up);

      boolean hasUpAlertSinceDown =
          lastUpAlert
              .map(up -> up.getSentAt().isAfter(lastDownAlert.get().getSentAt()))
              .orElse(false);

      if (!hasUpAlertSinceDown) {
        // Send UP alert to all active contacts
        if (hasEmailConfig) {
          for (AlertContact contact : emailContacts) {
            sendEmailAlert(monitor, contact, "up", 200, false);
          }
        }

        for (AlertContact contact : httpContacts) {
          sendHttpAlert(monitor, contact, "up", 200, null, false);
        }
      }
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
    alertHistory.setEmailSentTo(sentTo);
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
    } else {
      throw new IllegalArgumentException("Unsupported contact type: " + contact.getType());
    }

    log.info(
        "Sent test notification using production alert methods to {} ({})",
        contact.getValue(),
        contact.getName() != null ? contact.getName() : "unnamed contact");
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
