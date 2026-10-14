/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * An absolute http or https URL with a host, so the monitor can send a request to it. Null and
 * blank values pass: @NotBlank reports them.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = HttpUrlValidator.class)
@Documented
public @interface HttpUrl {
  String message() default "URL must start with http:// or https:// and name a host";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
