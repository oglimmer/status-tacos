/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.net.URI;
import java.net.URISyntaxException;

public class HttpUrlValidator implements ConstraintValidator<HttpUrl, String> {

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    if (value == null || value.isBlank()) {
      return true; // @NotBlank reports these
    }
    URI uri;
    try {
      uri = new URI(value);
    } catch (URISyntaxException e) {
      return false;
    }
    String scheme = uri.getScheme();
    // The raw authority, not the host: for a name with an underscore Java's URI has no host, but
    // the HTTP client sends to it anyway.
    String authority = uri.getRawAuthority();
    return scheme != null
        && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
        && authority != null
        && !authority.isBlank();
  }
}
