/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The preview in the editor. On an invalid template or CSS, html is null and error tells why: the
 * editor calls this while the user types, so it is not an HTTP error.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatusPagePreviewDto {

  private String html;
  private String error;

  /** Tags and attributes that the cleaning removed from the template. */
  private List<String> removed;
}
