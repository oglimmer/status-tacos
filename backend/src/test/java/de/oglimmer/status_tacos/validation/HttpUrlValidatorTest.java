/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.validation;

import static org.assertj.core.api.Assertions.assertThat;

import de.oglimmer.status_tacos.dto.MonitorRequestDto;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HttpUrlValidatorTest {

  private final HttpUrlValidator validator = new HttpUrlValidator();

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://example.com/health",
        "http://localhost:8080",
        "HTTP://EXAMPLE.COM",
        "http://my_service.local/metrics",
        "http://192.168.1.1/",
        "http://[::1]:8080/",
        "https://user:secret@example.com/"
      })
  void acceptsAnHttpUrlWithAHost(String url) {
    assertThat(validator.isValid(url, null)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "example.com/health",
        "/health",
        "ftp://example.com",
        "http://",
        "http://exa mple.com",
        "mailto:a@b.c",
        "javascript:alert(1)"
      })
  void rejectsAUrlTheMonitorCanNotSendTo(String url) {
    assertThat(validator.isValid(url, null)).isFalse();
  }

  @Test
  void leavesNullAndBlankToNotBlank() {
    assertThat(validator.isValid(null, null)).isTrue();
    assertThat(validator.isValid("  ", null)).isTrue();
  }

  @Test
  void aMonitorRequestWithABadUrlIsRejectedWithOneClearMessage() {
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      MonitorRequestDto request =
          MonitorRequestDto.builder().name("Shop").url("example.com").tenantId(1).build();

      Set<ConstraintViolation<MonitorRequestDto>> violations =
          factory.getValidator().validate(request);

      assertThat(violations)
          .extracting(ConstraintViolation::getMessage)
          .containsExactly("URL must start with http:// or https:// and name a host");
    }
  }
}
