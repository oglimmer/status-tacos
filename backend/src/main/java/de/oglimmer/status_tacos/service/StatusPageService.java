/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import de.oglimmer.status_tacos.dto.StatusPageConfigDto;
import de.oglimmer.status_tacos.dto.StatusPagePreviewDto;
import de.oglimmer.status_tacos.dto.StatusPageRequestDto;
import de.oglimmer.status_tacos.persistence.Tenant;
import de.oglimmer.status_tacos.persistence.TenantStatusPage;
import de.oglimmer.status_tacos.repository.TenantRepository;
import de.oglimmer.status_tacos.repository.TenantStatusPageRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The look of the public status page of a tenant: the editor. The caller checks the access. */
@Service
@RequiredArgsConstructor
public class StatusPageService {

  private final TenantRepository tenantRepository;
  private final TenantStatusPageRepository statusPageRepository;
  private final StatusPageRenderer renderer;
  private final PublicStatusService publicStatusService;

  @Transactional(readOnly = true)
  public StatusPageConfigDto getConfig(Integer tenantId) {
    Optional<TenantStatusPage> look = statusPageRepository.findById(tenantId);
    return StatusPageConfigDto.builder()
        .htmlTemplate(look.map(TenantStatusPage::getHtmlTemplate).orElse(null))
        .css(look.map(TenantStatusPage::getCss).orElse(null))
        .updatedAt(look.map(TenantStatusPage::getUpdatedAt).orElse(null))
        .defaultHtmlTemplate(renderer.getDefaultTemplate())
        .baseCss(renderer.getBaseCss())
        .starterCss(renderer.getStarterCss())
        .build();
  }

  /**
   * @throws IllegalArgumentException with a message for the user if the template or CSS is invalid
   */
  @Transactional
  public StatusPageConfigDto save(Integer tenantId, StatusPageRequestDto request) {
    String template = blankToNull(request.getHtmlTemplate());
    String css = blankToNull(request.getCss());
    renderer.validate(template, css);
    if (template == null && css == null) {
      return reset(tenantId);
    }
    TenantStatusPage look =
        statusPageRepository
            .findById(tenantId)
            .orElseGet(() -> TenantStatusPage.builder().tenantId(tenantId).build());
    look.setHtmlTemplate(template);
    look.setCss(css);
    statusPageRepository.save(look);
    publicStatusService.evict(tenantId);
    return getConfig(tenantId);
  }

  /** Back to the default look. */
  @Transactional
  public StatusPageConfigDto reset(Integer tenantId) {
    statusPageRepository.deleteById(tenantId);
    publicStatusService.evict(tenantId);
    return getConfig(tenantId);
  }

  /** Renders a look that is not saved yet, with the current data of the tenant. */
  public StatusPagePreviewDto preview(Integer tenantId, StatusPageRequestDto request) {
    Tenant tenant =
        tenantRepository
            .findById(tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
    String template = blankToNull(request.getHtmlTemplate());
    String css = blankToNull(request.getCss());
    try {
      renderer.validate(template, css);
      StatusPageRenderer.Rendered rendered =
          renderer.render(publicStatusService.getPageData(tenant), template, css, false);
      return StatusPagePreviewDto.builder()
          .html(rendered.html())
          .removed(List.copyOf(rendered.removed()))
          .build();
    } catch (IllegalArgumentException e) {
      return StatusPagePreviewDto.builder().error(e.getMessage()).removed(List.of()).build();
    }
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
