/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import de.oglimmer.status_tacos.dto.PublicMonitorStatusDto;
import de.oglimmer.status_tacos.dto.PublicStatusPageDto;
import de.oglimmer.status_tacos.dto.StatsPeriod;
import de.oglimmer.status_tacos.dto.StatusType;
import de.oglimmer.status_tacos.persistence.Monitor;
import de.oglimmer.status_tacos.persistence.MonitorStatus;
import de.oglimmer.status_tacos.persistence.Tenant;
import de.oglimmer.status_tacos.persistence.TenantStatusPage;
import de.oglimmer.status_tacos.repository.MonitorRepository;
import de.oglimmer.status_tacos.repository.TenantRepository;
import de.oglimmer.status_tacos.repository.TenantStatusPageAssetRepository;
import de.oglimmer.status_tacos.repository.TenantStatusPageAssetRepository.AssetInfo;
import de.oglimmer.status_tacos.repository.TenantStatusPageRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

/**
 * The public status page of a tenant: its monitors with {@code showOnPublicPage}, as data and as
 * the rendered HTML page. Anyone can call it without a sign-in, so the result is kept for {@link
 * #CACHE_TTL}: many calls do one database read. The cache key is the tenant ID, not the code from
 * the URL, so unknown codes do not fill it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PublicStatusService {

  static final Duration CACHE_TTL = Duration.ofSeconds(30);

  private final TenantRepository tenantRepository;
  private final MonitorRepository monitorRepository;
  private final TenantStatusPageRepository statusPageRepository;
  private final TenantStatusPageAssetRepository assetRepository;
  private final ServerProperties serverProperties;
  private final UptimeStatsService uptimeStatsService;
  private final StatusPageRenderer renderer;
  private final Clock clock;

  private record Cached(Instant until, PublicStatusPageDto page, String html) {}

  private final Map<Integer, Cached> cache = new ConcurrentHashMap<>();

  /**
   * @return empty if there is no active tenant with the code
   */
  public Optional<PublicStatusPageDto> getPublicStatusPage(String tenantCode) {
    return tenantRepository.findActiveByCode(tenantCode).map(t -> cached(t).page());
  }

  /**
   * The page as a full HTML document. Send it with {@link
   * StatusPageRenderer#CONTENT_SECURITY_POLICY}.
   *
   * @return empty if there is no active tenant with the code
   */
  public Optional<String> getPublicStatusHtml(String tenantCode) {
    return tenantRepository.findActiveByCode(tenantCode).map(t -> cached(t).html());
  }

  /** The data of the page, for the preview in the editor. */
  public PublicStatusPageDto getPageData(Tenant tenant) {
    return cached(tenant).page();
  }

  /**
   * The URL of each uploaded file of the tenant, by name. The path starts at the root of the host
   * that serves the page, so it works on the public page, in a frame on any site and in the editor.
   * The hash in the URL changes with the file, so browsers can keep a file for a long time.
   */
  public Map<String, String> getAssetUrls(Tenant tenant) {
    String contextPath = serverProperties.getServlet().getContextPath();
    String base =
        (contextPath == null ? "" : contextPath)
            + "/v1/public/"
            + UriUtils.encodePathSegment(tenant.getCode(), StandardCharsets.UTF_8)
            + "/status/assets/";
    return assetRepository.findByTenantIdOrderByName(tenant.getId()).stream()
        .collect(
            Collectors.toMap(
                AssetInfo::getName,
                a -> base + a.getName() + "?v=" + PublicStatusService.version(a.getSha256())));
  }

  /** The part of the hash that the URL of a file uses. */
  static String version(String sha256) {
    return sha256.substring(0, 16);
  }

  /** Call it when the look of the page changes. Other instances see it after {@link #CACHE_TTL}. */
  public void evict(Integer tenantId) {
    cache.remove(tenantId);
  }

  private Cached cached(Tenant tenant) {
    Instant now = clock.instant();
    return cache.compute(
        tenant.getId(),
        (id, cached) ->
            cached != null && now.isBefore(cached.until()) ? cached : load(tenant, now));
  }

  // The query fetches the status with the monitor, so this needs no open transaction.
  private Cached load(Tenant tenant, Instant now) {
    List<Monitor> monitors = monitorRepository.findShownOnPublicPageWithStatus(tenant.getId());
    Map<Integer, BigDecimal> uptime =
        uptimeStatsService.getUptimePercentages(monitors, StatsPeriod.NINETY_DAYS);
    PublicStatusPageDto page =
        PublicStatusPageDto.builder()
            .tenantName(tenant.getName())
            .monitors(monitors.stream().map(m -> toDto(m, uptime.get(m.getId()))).toList())
            .build();
    return new Cached(now.plus(CACHE_TTL), page, renderHtml(tenant, page));
  }

  private String renderHtml(Tenant tenant, PublicStatusPageDto page) {
    Optional<TenantStatusPage> look = statusPageRepository.findById(tenant.getId());
    String template = look.map(TenantStatusPage::getHtmlTemplate).orElse(null);
    String css = look.map(TenantStatusPage::getCss).orElse(null);
    try {
      return renderer.render(page, template, css, getAssetUrls(tenant), true).html();
    } catch (IllegalArgumentException e) {
      // Saved looks are checked, so only a stricter check of a newer version gets here.
      log.warn("Status page of tenant {} is invalid, using the default: {}", tenant.getId(), e);
      return renderer.render(page, null, null, true).html();
    }
  }

  private static PublicMonitorStatusDto toDto(Monitor monitor, BigDecimal uptime) {
    MonitorStatus status = monitor.getMonitorStatus();
    StatusType current =
        status == null ? null : StatusType.valueOf(status.getCurrentStatus().name());
    return PublicMonitorStatusDto.builder()
        .name(monitor.getName())
        .currentStatus(current)
        .downSince(
            current == StatusType.down
                ? (status.getOutageStartedAt() != null
                    ? status.getOutageStartedAt()
                    : status.getLastDownAt())
                : null)
        .uptimePercentage90d(uptime)
        .build();
  }
}
