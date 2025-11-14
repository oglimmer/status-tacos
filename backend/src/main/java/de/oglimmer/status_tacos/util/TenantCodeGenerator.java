/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.util;

import de.oglimmer.status_tacos.service.TenantService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TenantCodeGenerator {

  private final TenantService tenantService;

  public String generateUniqueTenantCode(String baseName) {
    // Sanitize the base name for tenant code
    String baseCode = baseName.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
    if (baseCode.isEmpty()) {
      baseCode = "user";
    }

    // Try the base code first
    if (!tenantService.tenantExists(baseCode)) {
      return baseCode;
    }

    // If base code exists, try with UUID suffix
    String uniqueCode = baseCode + "-" + UUID.randomUUID().toString().substring(0, 8);

    // Ensure it doesn't exceed database limits and is unique
    while (tenantService.tenantExists(uniqueCode)) {
      uniqueCode = baseCode + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    return uniqueCode;
  }
}
