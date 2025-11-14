/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.mapper;

import de.oglimmer.status_tacos.dto.*;
import de.oglimmer.status_tacos.persistence.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface EntityMapper {

  EntityMapper INSTANCE = Mappers.getMapper(EntityMapper.class);

  @Mapping(
      target = "tenantIds",
      expression =
          "java(user.getTenants() != null ? user.getTenants().stream().map(Tenant::getId).toArray(Integer[]::new) : new Integer[0])")
  @Mapping(
      target = "tenants",
      expression =
          "java(user.getTenants() != null ? user.getTenants().stream().map(this::toDto).collect(java.util.stream.Collectors.toList()) : java.util.Collections.emptyList())")
  UserResponseDto toDto(User user);

  @Mapping(target = "tenantId", source = "tenant.id")
  @Mapping(target = "tenant", source = "tenant")
  @Mapping(target = "httpHeaders", source = "httpHeaders")
  @Mapping(target = "statusCodeRegex", source = "statusCodeRegex")
  @Mapping(target = "responseBodyRegex", source = "responseBodyRegex")
  @Mapping(target = "prometheusKey", source = "prometheusKey")
  @Mapping(target = "prometheusMinValue", source = "prometheusMinValue")
  @Mapping(target = "prometheusMaxValue", source = "prometheusMaxValue")
  @Mapping(target = "alertingThreshold", source = "alertingThreshold")
  MonitorResponseDto toDto(Monitor monitor);

  TenantResponseDto toDto(Tenant tenant);

  @Mapping(target = "monitorName", source = "monitor.name")
  @Mapping(target = "monitorUrl", source = "monitor.url")
  @Mapping(target = "tenantId", source = "monitor.tenant.id")
  @Mapping(target = "tenant", source = "monitor.tenant")
  MonitorStatusResponseDto toDto(MonitorStatus monitorStatus);
}
