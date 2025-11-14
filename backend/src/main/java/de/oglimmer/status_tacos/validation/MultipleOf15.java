/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MultipleOf15Validator.class)
@Documented
public @interface MultipleOf15 {
  String message() default "Value must be a multiple of 15 seconds";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
