/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import de.oglimmer.status_tacos.dto.MonitorRequestDto;
import de.oglimmer.status_tacos.persistence.Monitor;
import de.oglimmer.status_tacos.persistence.MonitorState;
import de.oglimmer.status_tacos.repository.MonitorRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MonitorService {

  private final MonitorRepository monitorRepository;

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
            .state(requestDto.getState())
            .tenantId(tenantId)
            .httpHeaders(requestDto.getHttpHeaders())
            .statusCodeRegex(requestDto.getStatusCodeRegex())
            .responseBodyRegex(requestDto.getResponseBodyRegex())
            .prometheusKey(requestDto.getPrometheusKey())
            .prometheusMinValue(requestDto.getPrometheusMinValue())
            .prometheusMaxValue(requestDto.getPrometheusMaxValue())
            .alertingThreshold(requestDto.getAlertingThreshold())
            .build();

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
    existingMonitor.setState(requestDto.getState());
    existingMonitor.setHttpHeaders(requestDto.getHttpHeaders());
    existingMonitor.setStatusCodeRegex(requestDto.getStatusCodeRegex());
    existingMonitor.setResponseBodyRegex(requestDto.getResponseBodyRegex());
    existingMonitor.setPrometheusKey(requestDto.getPrometheusKey());
    existingMonitor.setPrometheusMinValue(requestDto.getPrometheusMinValue());
    existingMonitor.setPrometheusMaxValue(requestDto.getPrometheusMaxValue());
    existingMonitor.setAlertingThreshold(requestDto.getAlertingThreshold());

    Monitor updatedMonitor = monitorRepository.save(existingMonitor);
    log.info("Monitor updated: {}", updatedMonitor.getId());

    return updatedMonitor;
  }

  public void deleteMonitor(Integer tenantId, Integer id) {
    log.info("Deleting monitor ID: {}", id);

    Monitor monitor =
        monitorRepository
            .findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Monitor not found with ID: " + id));

    monitorRepository.delete(monitor);
    log.info("Monitor deleted: {}", id);
  }

  public Monitor updateMonitorState(Integer tenantId, Integer id, MonitorState newState) {
    log.info("Updating state for monitor ID: {} to {}", id, newState);

    Monitor monitor =
        monitorRepository
            .findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Monitor not found with ID: " + id));

    monitor.setState(newState);
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
}
