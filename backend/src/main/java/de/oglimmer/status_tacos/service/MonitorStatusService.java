/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import de.oglimmer.status_tacos.persistence.*;
import de.oglimmer.status_tacos.repository.MonitorRepository;
import de.oglimmer.status_tacos.repository.MonitorStatusRepository;
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
  private final MonitorRepository monitorRepository;
  private final TxDebug txDebug;

  public MonitorStatus updateMonitorStatus(
      Integer tenantId, Monitor monitor, CheckResult checkResult) {
    log.debug(
        "Updating monitor status for monitor {}: {} at {}",
        monitor.getId(),
        checkResult.getIsUp(),
        System.nanoTime());

    Optional<MonitorStatus> existingStatus =
        monitorStatusRepository.findByMonitorIdAndTenantId(monitor.getId(), tenantId);

    txDebug.checkIfManaged(monitor);

    MonitorStatus status;
    if (existingStatus.isPresent()) {
      status = existingStatus.get();
      txDebug.checkIfManaged(status);
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
    } else {
      status.setLastDownAt(checkResult.getCheckedAt());
      status.setConsecutiveFailures(status.getConsecutiveFailures() + 1);
    }

    MonitorStatus savedStatus = monitorStatusRepository.save(status);

    if (statusChanged) {
      log.info(
          "Monitor {} status changed to: {} (consecutive failures: {})",
          monitor.getId(),
          newStatus,
          savedStatus.getConsecutiveFailures());
    }

    return savedStatus;
  }

  @Transactional(readOnly = true)
  public List<MonitorStatus> getAllActiveMonitorStatuses(Integer tenantId) {
    log.debug("Getting all active monitor statuses");
    return monitorStatusRepository.findAllActiveMonitorStatusesByTenantId(tenantId);
  }

  @Transactional(readOnly = true)
  public List<MonitorStatus> getMonitorsWithConsecutiveFailures(Integer tenantId, int threshold) {
    log.debug("Getting monitors with consecutive failures >= {}", threshold);
    return monitorStatusRepository.findByTenantIdAndConsecutiveFailuresGreaterThanEqual(
        tenantId, threshold);
  }
}
