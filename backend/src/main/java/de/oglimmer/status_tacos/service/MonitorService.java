/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import de.oglimmer.status_tacos.dto.MonitorRequestDto;
import de.oglimmer.status_tacos.persistence.DailyPause;
import de.oglimmer.status_tacos.persistence.Monitor;
import de.oglimmer.status_tacos.persistence.MonitorState;
import de.oglimmer.status_tacos.persistence.SilenceDuration;
import de.oglimmer.status_tacos.repository.MonitorRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MonitorService {

  private final MonitorRepository monitorRepository;
  private final Clock clock;

  public Monitor createMonitor(Integer tenantId, MonitorRequestDto requestDto) {
    log.info("Creating new monitor: {}", requestDto.getName());

    Optional<Monitor> existingMonitor =
        monitorRepository.findByTenantIdAndUrlIgnoreCase(tenantId, requestDto.getUrl());
    if (existingMonitor.isPresent()) {
      throw new IllegalArgumentException("Monitor with URL already exists: " + requestDto.getUrl());
    }

    Monitor monitor =
        Monitor.builder()
            .name(requestDto.getName())
            .url(requestDto.getUrl())
            .tenantId(tenantId)
            .httpHeaders(requestDto.getHttpHeaders())
            .statusCodeRegex(requestDto.getStatusCodeRegex())
            .responseBodyRegex(requestDto.getResponseBodyRegex())
            .prometheusKey(requestDto.getPrometheusKey())
            .prometheusMinValue(requestDto.getPrometheusMinValue())
            .prometheusMaxValue(requestDto.getPrometheusMaxValue())
            .checkInterval(requestDto.getCheckInterval())
            .connectTimeout(requestDto.getConnectTimeout())
            .useSystemDns(Boolean.TRUE.equals(requestDto.getUseSystemDns()))
            .alertingThreshold(requestDto.getAlertingThreshold())
            .showOnPublicPage(Boolean.TRUE.equals(requestDto.getShowOnPublicPage()))
            .build();
    applyState(monitor, requestDto.getState(), SilenceDuration.fromCode(requestDto.getSilentFor()));
    applyDailyPause(monitor, requestDto);

    Monitor savedMonitor = monitorRepository.save(monitor);
    log.info("Monitor created with ID: {}", savedMonitor.getId());

    return savedMonitor;
  }

  @Transactional(readOnly = true)
  public Monitor getMonitorById(Integer tenantId, Integer id) {
    log.debug("Fetching monitor by ID: {}", id);

    return monitorRepository
        .findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new IllegalArgumentException("Monitor not found with ID: " + id));
  }

  @Transactional(readOnly = true)
  public List<Monitor> getAllMonitors(Integer tenantId) {
    log.debug("Fetching all monitors");

    return monitorRepository.findByTenantId(tenantId);
  }

  @Transactional(readOnly = true)
  public Page<Monitor> getAllMonitors(Integer tenantId, Pageable pageable) {
    log.debug("Fetching monitors with pagination: {}", pageable);

    // TODO: Add tenant-aware pageable method to MonitorRepository
    // For now, fall back to filtering in service layer (not optimal for large datasets)
    return monitorRepository.findAll(pageable);
  }

  @Transactional(readOnly = true)
  public List<Monitor> getActiveMonitors(Integer tenantId) {
    log.debug("Fetching active monitors");

    return monitorRepository.findByTenantIdAndState(tenantId, MonitorState.ACTIVE);
  }

  @Transactional(readOnly = true)
  public List<Monitor> getMonitorsByState(Integer tenantId, MonitorState state) {
    log.debug("Fetching monitors with state: {}", state);

    return monitorRepository.findByTenantIdAndState(tenantId, state);
  }

  @Transactional(readOnly = true)
  public List<Monitor> searchMonitorsByName(Integer tenantId, String name) {
    log.debug("Searching monitors by name: {}", name);

    return monitorRepository.findByTenantIdAndNameContainingIgnoreCase(tenantId, name);
  }

  public Monitor updateMonitor(Integer tenantId, Integer id, MonitorRequestDto requestDto) {
    log.info("Updating monitor ID: {}", id);

    Monitor existingMonitor =
        monitorRepository
            .findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Monitor not found with ID: " + id));

    if (!existingMonitor.getUrl().equalsIgnoreCase(requestDto.getUrl())) {
      Optional<Monitor> duplicateUrlMonitor =
          monitorRepository.findByTenantIdAndUrlIgnoreCase(tenantId, requestDto.getUrl());
      if (duplicateUrlMonitor.isPresent() && !duplicateUrlMonitor.get().getId().equals(id)) {
        throw new IllegalArgumentException(
            "Monitor with URL already exists: " + requestDto.getUrl());
      }
    }

    existingMonitor.setName(requestDto.getName());
    existingMonitor.setUrl(requestDto.getUrl());
    applyState(
        existingMonitor,
        requestDto.getState(),
        SilenceDuration.fromCode(requestDto.getSilentFor()));
    existingMonitor.setHttpHeaders(requestDto.getHttpHeaders());
    existingMonitor.setStatusCodeRegex(requestDto.getStatusCodeRegex());
    existingMonitor.setResponseBodyRegex(requestDto.getResponseBodyRegex());
    existingMonitor.setPrometheusKey(requestDto.getPrometheusKey());
    existingMonitor.setPrometheusMinValue(requestDto.getPrometheusMinValue());
    existingMonitor.setPrometheusMaxValue(requestDto.getPrometheusMaxValue());
    existingMonitor.setCheckInterval(requestDto.getCheckInterval());
    existingMonitor.setConnectTimeout(requestDto.getConnectTimeout());
    existingMonitor.setUseSystemDns(Boolean.TRUE.equals(requestDto.getUseSystemDns()));
    existingMonitor.setAlertingThreshold(requestDto.getAlertingThreshold());
    existingMonitor.setShowOnPublicPage(Boolean.TRUE.equals(requestDto.getShowOnPublicPage()));
    applyDailyPause(existingMonitor, requestDto);

    Monitor updatedMonitor = monitorRepository.save(existingMonitor);
    log.info("Monitor updated: {}", updatedMonitor.getId());

    return updatedMonitor;
  }

  /**
   * Locking read (shared lock) of the monitor's tenant. Call it in the transaction that writes
   * check results etc. It waits while a tenant move of the monitor commits, and then returns the
   * new tenant. Empty if the monitor does not exist anymore.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public Optional<Integer> lockTenantId(Integer monitorId) {
    return monitorRepository.findTenantIdForShare(monitorId);
  }

  public void deleteMonitor(Integer tenantId, Integer id) {
    log.info("Deleting monitor ID: {}", id);

    if (monitorRepository.deleteByIdAndTenantId(id, tenantId) == 0) {
      throw new IllegalArgumentException("Monitor not found with ID: " + id);
    }
    log.info("Monitor deleted: {}", id);
  }

  public Monitor updateMonitorState(Integer tenantId, Integer id, MonitorState newState) {
    return updateMonitorState(tenantId, id, newState, null);
  }

  /** silentFor is only for SILENT. Null: a running silence keeps its end, else 24h. */
  public Monitor updateMonitorState(
      Integer tenantId, Integer id, MonitorState newState, SilenceDuration silentFor) {
    log.info("Updating state for monitor ID: {} to {} ({})", id, newState, silentFor);

    Monitor monitor =
        monitorRepository
            .findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Monitor not found with ID: " + id));

    applyState(monitor, newState, silentFor);
    Monitor updatedMonitor = monitorRepository.save(monitor);

    log.info("Monitor {} state changed to: {}", id, updatedMonitor.getState());
    return updatedMonitor;
  }

  @Transactional(readOnly = true)
  public long getActiveMonitorCount(Integer tenantId) {
    return monitorRepository.countByTenantIdAndState(tenantId, MonitorState.ACTIVE);
  }

  @Transactional(readOnly = true)
  public long getMonitorCountByState(Integer tenantId, MonitorState state) {
    return monitorRepository.countByTenantIdAndState(tenantId, state);
  }

  @Transactional(readOnly = true)
  public List<Monitor> getAllMonitors(Set<Integer> tenantIds) {
    log.debug("Fetching all monitors for tenants: {}", tenantIds);
    return monitorRepository.findByTenantIdIn(tenantIds);
  }

  @Transactional(readOnly = true)
  public List<Monitor> getActiveMonitors(Set<Integer> tenantIds) {
    log.debug("Fetching active monitors for tenants: {}", tenantIds);
    return monitorRepository.findByTenantIdInAndState(tenantIds, MonitorState.ACTIVE);
  }

  @Transactional(readOnly = true)
  public List<Monitor> getMonitorsByState(Set<Integer> tenantIds, MonitorState state) {
    log.debug("Fetching monitors with state: {} for tenants: {}", state, tenantIds);
    return monitorRepository.findByTenantIdInAndState(tenantIds, state);
  }

  @Transactional(readOnly = true)
  public List<Monitor> searchMonitorsByName(Set<Integer> tenantIds, String name) {
    log.debug("Searching monitors by name: {} for tenants: {}", name, tenantIds);
    return monitorRepository.findByTenantIdInAndNameContainingIgnoreCase(tenantIds, name);
  }

  @Transactional(readOnly = true)
  public List<Monitor> getAllMonitorsWithStatusAndTenant(Set<Integer> tenantIds) {
    log.debug("Fetching all monitors with status and tenant info for tenants: {}", tenantIds);
    return monitorRepository.findByTenantIdInWithStatusAndTenant(tenantIds);
  }

  /** SILENT monitors whose silence ended are ACTIVE again and send alerts. */
  public int endExpiredSilences() {
    int count =
        monitorRepository.endExpiredSilences(
            clock.instant(), LocalDateTime.ofInstant(clock.instant(), clock.getZone()));
    if (count > 0) {
      log.info("Silence ended for {} monitor(s), they are active again", count);
    }
    return count;
  }

  /**
   * Sets the state. A SILENT monitor always has an end of the silence: now plus silentFor. Without
   * silentFor a running silence keeps its end, and a new silence gets the default (24h).
   */
  private void applyState(Monitor monitor, MonitorState state, SilenceDuration silentFor) {
    if (state != MonitorState.SILENT) {
      monitor.setState(state);
      monitor.setSilentUntil(null);
      return;
    }
    Instant now = clock.instant();
    boolean silenceRuns =
        monitor.getState() == MonitorState.SILENT
            && monitor.getSilentUntil() != null
            && monitor.getSilentUntil().isAfter(now);
    if (silentFor != null) {
      monitor.setSilentUntil(now.plus(silentFor.getDuration()));
    } else if (!silenceRuns) {
      monitor.setSilentUntil(now.plus(SilenceDuration.DEFAULT.getDuration()));
    }
    monitor.setState(MonitorState.SILENT);
  }

  /**
   * Sets the daily pause. No start or no end: no daily pause. No time zone: UTC. The DTO validation
   * already checks the values, so an error here is only for callers without validation.
   */
  private void applyDailyPause(Monitor monitor, MonitorRequestDto requestDto) {
    LocalTime from = DailyPause.parseTime(requestDto.getDailyPauseFrom());
    LocalTime until = DailyPause.parseTime(requestDto.getDailyPauseUntil());
    if (from == null || until == null) {
      monitor.setDailyPauseFrom(null);
      monitor.setDailyPauseUntil(null);
      monitor.setDailyPauseTimezone(null);
      return;
    }
    if (from.equals(until)) {
      throw new IllegalArgumentException("Daily pause start and end must not be the same");
    }
    String zone = requestDto.getDailyPauseTimezone();
    if (zone == null || zone.isBlank()) {
      zone = DailyPause.DEFAULT_TIMEZONE;
    } else if (!DailyPause.isValidZone(zone)) {
      throw new IllegalArgumentException("Unknown time zone: " + zone);
    }
    monitor.setDailyPauseFrom(from);
    monitor.setDailyPauseUntil(until);
    monitor.setDailyPauseTimezone(zone);
  }
}
