/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A look of the public status page. A blank value means the default. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatusPageRequestDto {

  private String htmlTemplate;
  private String css;
}
