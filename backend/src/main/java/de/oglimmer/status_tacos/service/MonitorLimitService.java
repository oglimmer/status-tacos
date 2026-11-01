/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import de.oglimmer.status_tacos.config.LimitsConfig;
import de.oglimmer.status_tacos.config.PeerConfig;
import de.oglimmer.status_tacos.persistence.User;
import de.oglimmer.status_tacos.repository.MonitorRepository;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The maximum number of monitors of a user. It counts the monitors in all tenants of the user, so a
 * monitor of a shared tenant counts for each member. The monitors of the peer tenant do not count:
 * they watch the server itself.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MonitorLimitService {

  private final LimitsConfig limitsConfig;
  private final MonitorRepository monitorRepository;
  private final PeerConfig peerConfig;

  /** The limit of the user, or null if the user has no limit. */
  public Integer maxMonitorsFor(User user) {
    int max =
        user.getMaxMonitors() != null
            ? user.getMaxMonitors()
            : limitsConfig.getMaxMonitorsPerUser();
    return max > 0 ? max : null;
  }

  /** Throws if the user has the maximum number of monitors in these tenants already. */
  public void checkCanCreateMonitor(User user, Set<Integer> tenantIds) {
    checkCanCreateMonitor(user, tenantIds, null);
  }

  /**
   * Throws if the user has the maximum number of monitors in these tenants already. A monitor in
   * the peer tenant can always be created.
   *
   * @param targetTenantId the tenant of the new monitor, or null if not known
   */
  public void checkCanCreateMonitor(User user, Set<Integer> tenantIds, Integer targetTenantId) {
    Integer max = maxMonitorsFor(user);
    if (max == null || peerConfig.isPeerTenant(targetTenantId)) {
      return;
    }
    Set<Integer> counted = new HashSet<>(tenantIds);
    if (peerConfig.isEnabled()) {
      counted.remove(peerConfig.getTenantId());
    }
    if (!counted.isEmpty() && monitorRepository.countByTenantIdIn(counted) >= max) {
      throw new MonitorLimitReachedException(max);
    }
  }

  public static class MonitorLimitReachedException extends RuntimeException {
    public MonitorLimitReachedException(int max) {
      super(
          "You have reached the maximum of "
              + max
              + " monitors. Delete a monitor, or run your own Status Tacos backend for more.");
    }
  }
}
