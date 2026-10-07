/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import de.oglimmer.status_tacos.persistence.AlertContact;
import de.oglimmer.status_tacos.persistence.Monitor;
import de.oglimmer.status_tacos.persistence.Tenant;
import de.oglimmer.status_tacos.repository.AlertContactRepository;
import de.oglimmer.status_tacos.repository.AlertHistoryRepository;
import de.oglimmer.status_tacos.repository.CheckResultRepository;
import de.oglimmer.status_tacos.repository.MonitorRepository;
import de.oglimmer.status_tacos.repository.MonitorStatusRepository;
import de.oglimmer.status_tacos.repository.TenantRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Moves a monitor and all its history to another tenant.
 *
 * <p>A monitor can have hundreds of thousands of check results. One UPDATE over all of them runs
 * for minutes, while the scheduler writes new check results for the same monitor. MariaDB then
 * aborts the UPDATE ("Record has changed since last read"). So the move has two phases:
 *
 * <ol>
 *   <li>Old check results move in small checked_at ranges, one short transaction per range. The
 *       monitor stays in the old tenant. A failed run can be started again, it continues.
 *   <li>One short final transaction locks the monitor row and moves the rest: the newest check
 *       results, status, alert history and the monitor itself. Check writers read the tenant with a
 *       shared lock ({@link MonitorService#lockTenantId}), so they wait for this transaction and
 *       then write with the new tenant.
 * </ol>
 *
 * <p>The uptime roll-ups and outages have no tenant column, so they need no move.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MonitorMoveService {

  static final Duration BATCH_RANGE = Duration.ofDays(1);
  // The final transaction moves check results of this last period, so it also gets rows that were
  // written while phase 1 ran.
  static final Duration FINAL_OVERLAP = Duration.ofMinutes(10);
  static final int MAX_ATTEMPTS = 5;

  private final MonitorRepository monitorRepository;
  private final CheckResultRepository checkResultRepository;
  private final AlertHistoryRepository alertHistoryRepository;
  private final MonitorStatusRepository monitorStatusRepository;
  private final AlertContactRepository alertContactRepository;
  private final TenantRepository tenantRepository;
  private final TransactionTemplate transactionTemplate;

  /**
   * Moves the monitor and all its history (check results, status, alert history) to another tenant.
   * Alert contacts of the old tenant that are limited to selected monitors lose this monitor. Such
   * a contact is deleted when this monitor was its last one.
   *
   * @throws IllegalArgumentException if the monitor is not in one of {@code sourceTenantIds}
   * @throws IllegalStateException if the move is not possible (same tenant, unknown tenant, URL
   *     already used in the target tenant)
   */
  public Monitor moveMonitorToTenant(
      Set<Integer> sourceTenantIds, Integer id, Integer targetTenantId) {
    log.info("Moving monitor ID: {} to tenant: {}", id, targetTenantId);
    long startTime = System.currentTimeMillis();

    inTransaction(() -> validate(loadMonitor(id, sourceTenantIds, false), targetTenantId));

    LocalDateTime finalFrom = LocalDateTime.now().minus(FINAL_OVERLAP);
    int checkResults = moveOldCheckResults(id, targetTenantId, finalFrom);

    Monitor movedMonitor =
        inTransaction(() -> moveRest(sourceTenantIds, id, targetTenantId, finalFrom));

    log.info(
        "Monitor {} moved to tenant {} in {}ms ({} old check results in batches)",
        id,
        targetTenantId,
        System.currentTimeMillis() - startTime,
        checkResults);
    return movedMonitor;
  }

  private int moveOldCheckResults(Integer id, Integer targetTenantId, LocalDateTime until) {
    LocalDateTime oldest =
        inTransaction(() -> checkResultRepository.findOldestCheckedAt(id).orElse(until));
    int moved = 0;
    for (LocalDateTime from = oldest; from.isBefore(until); ) {
      LocalDateTime rangeFrom = from;
      LocalDateTime rangeTo = min(from.plus(BATCH_RANGE), until);
      moved +=
          inTransaction(
              () ->
                  checkResultRepository.updateTenantIdByMonitorIdInRange(
                      id, targetTenantId, rangeFrom, rangeTo));
      from = rangeTo;
    }
    return moved;
  }

  private Monitor moveRest(
      Set<Integer> sourceTenantIds, Integer id, Integer targetTenantId, LocalDateTime finalFrom) {
    Monitor monitor = loadMonitor(id, sourceTenantIds, true);
    Tenant targetTenant = validate(monitor, targetTenantId);

    removeFromScopedAlertContacts(monitor);

    checkResultRepository.updateTenantIdByMonitorIdInRange(
        id, targetTenantId, finalFrom, LocalDateTime.now().plusDays(1));
    alertHistoryRepository.updateTenantIdByMonitorId(id, targetTenantId);
    monitorStatusRepository.updateTenantIdByMonitorId(id, targetTenantId);

    monitor.setTenantId(targetTenantId);
    monitor.setTenant(targetTenant);
    return monitorRepository.save(monitor);
  }

  private Monitor loadMonitor(Integer id, Set<Integer> sourceTenantIds, boolean forUpdate) {
    return (forUpdate ? monitorRepository.findByIdForUpdate(id) : monitorRepository.findById(id))
        .filter(m -> sourceTenantIds.contains(m.getTenantId()))
        .orElseThrow(() -> new IllegalArgumentException("Monitor not found with ID: " + id));
  }

  private Tenant validate(Monitor monitor, Integer targetTenantId) {
    if (monitor.getTenantId().equals(targetTenantId)) {
      throw new IllegalStateException("Monitor is already in tenant: " + targetTenantId);
    }
    Tenant targetTenant =
        tenantRepository
            .findById(targetTenantId)
            .orElseThrow(
                () -> new IllegalStateException("Tenant not found with ID: " + targetTenantId));
    if (monitorRepository
        .findByTenantIdAndUrlIgnoreCase(targetTenantId, monitor.getUrl())
        .isPresent()) {
      throw new IllegalStateException(
          "Monitor with URL already exists in target tenant: " + monitor.getUrl());
    }
    return targetTenant;
  }

  private void removeFromScopedAlertContacts(Monitor monitor) {
    for (AlertContact contact : alertContactRepository.findScopedToMonitor(monitor.getId())) {
      contact.getMonitors().remove(monitor);
      if (contact.getMonitors().isEmpty()) {
        alertContactRepository.delete(contact);
        log.info(
            "Alert contact {} deleted, monitor {} was its only monitor",
            contact.getId(),
            monitor.getId());
      } else {
        log.info("Monitor {} removed from alert contact {}", monitor.getId(), contact.getId());
      }
    }
  }

  /** Runs the action in a new transaction. Repeats it when it collides with a concurrent write. */
  private <T> T inTransaction(Supplier<T> action) {
    for (int attempt = 1; ; attempt++) {
      try {
        return transactionTemplate.execute(status -> action.get());
      } catch (ConcurrencyFailureException e) {
        if (attempt >= MAX_ATTEMPTS) {
          throw e;
        }
        log.warn("Monitor move step collided with a concurrent write, retry {}", attempt);
        sleep(100L * attempt);
      }
    }
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Monitor move interrupted", e);
    }
  }

  private static LocalDateTime min(LocalDateTime a, LocalDateTime b) {
    return a.isBefore(b) ? a : b;
  }
}
