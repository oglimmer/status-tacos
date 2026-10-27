/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A file of the public status page, for the editor. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatusPageAssetDto {

  private String name;
  private String contentType;
  private Integer size;
  private LocalDateTime updatedAt;

  /** What to write in the CSS or the template, for example {@code asset:inter.woff2}. */
  private String reference;
}
