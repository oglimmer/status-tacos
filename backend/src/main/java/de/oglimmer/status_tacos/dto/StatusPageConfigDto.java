/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** The look of the public status page of a tenant, for the editor. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatusPageConfigDto {

  /** Null: the default template. */
  private String htmlTemplate;

  /** Null: no tenant CSS. */
  private String css;

  /** Null when the tenant never saved a look. */
  private LocalDateTime updatedAt;

  /** The default template, as a start for an own template. */
  private String defaultHtmlTemplate;

  /** The base CSS. It is always on the page, in the layer below the tenant CSS. */
  private String baseCss;

  /** A start for the tenant CSS: the variables of the base CSS. */
  private String starterCss;
}
