/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import de.oglimmer.status_tacos.persistence.*;
import de.oglimmer.status_tacos.repository.MonitorStatusRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MonitorStatusService {

  private final MonitorStatusRepository monitorStatusRepository;

  public MonitorStatus updateMonitorStatus(
      Integer tenantId, Monitor monitor, CheckResult checkResult) {
    log.debug(
        "Updating monitor status for monitor {}: {} at {}",
        monitor.getId(),
        checkResult.getIsUp(),
        System.nanoTime());

    Optional<MonitorStatus> existingStatus =
        monitorStatusRepository.findByMonitorIdAndTenantId(monitor.getId(), tenantId);

    MonitorStatus status;
    if (existingStatus.isPresent()) {
      status = existingStatus.get();
    } else {
      log.info("Creating new monitor status for monitor: {}", monitor.getId());
      status = new MonitorStatus();

      status.setMonitorId(monitor.getId());
      status.setTenantId(tenantId);
      //            status.setMonitor(monitor); // This is not needed as we set the monitorId
      // directly and monitor is detached anyway
      status.setConsecutiveFailures(0);
    }

    MonitorStatus.StatusType newStatus =
        checkResult.getIsUp() ? MonitorStatus.StatusType.up : MonitorStatus.StatusType.down;

    boolean statusChanged = status.getCurrentStatus() != newStatus;

    status.setCurrentStatus(newStatus);
    status.setLastCheckedAt(checkResult.getCheckedAt());
    status.setLastResponseTimeMs(checkResult.getResponseTimeMs());
    status.setLastStatusCode(checkResult.getStatusCode());

    if (checkResult.getIsUp()) {
      status.setLastUpAt(checkResult.getCheckedAt());
      status.setConsecutiveFailures(0);
      // outageStartedAt stays: the UP alert reports the start of the outage that just ended
    } else {
      status.setLastDownAt(checkResult.getCheckedAt());
      status.setConsecutiveFailures(status.getConsecutiveFailures() + 1);
      if (statusChanged || status.getOutageStartedAt() == null) {
        status.setOutageStartedAt(checkResult.getCheckedAt());
      }
    }

    MonitorStatus savedStatus = monitorStatusRepository.save(status);

    // Logged once per change, not for each failed check
    if (statusChanged && checkResult.getIsUp()) {
      log.info("Monitor {} ({}) is UP", monitor.getId(), monitor.getName());
    } else if (statusChanged) {
      log.warn(
          "Monitor {} ({}) is DOWN: {}",
          monitor.getId(),
          monitor.getName(),
          checkResult.getErrorMessage());
    }

    return savedStatus;
  }

  /** Statuses of the ACTIVE and SILENT monitors of the tenants, in one query. */
  @Transactional(readOnly = true)
  public List<MonitorStatus> getAllActiveMonitorStatuses(Collection<Integer> tenantIds) {
    log.debug("Getting all active monitor statuses for tenants: {}", tenantIds);
    return tenantIds.isEmpty()
        ? List.of()
        : monitorStatusRepository.findAllActiveMonitorStatusesByTenantIdIn(tenantIds);
  }
}
