/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class MultipleOf15Validator implements ConstraintValidator<MultipleOf15, Integer> {

  @Override
  public void initialize(MultipleOf15 constraintAnnotation) {
    // No initialization needed
  }

  @Override
  public boolean isValid(Integer value, ConstraintValidatorContext context) {
    if (value == null) {
      return true; // Let @NotNull handle null validation
    }
    return value % 15 == 0;
  }
}
