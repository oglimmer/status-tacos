/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.config;

import org.springframework.boot.autoconfigure.web.servlet.error.ErrorViewResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;

/**
 * Replaces the Whitelabel page of Spring Boot with the branded {@link ErrorPage}. Spring Boot uses
 * this only when the client accepts {@code text/html}; API clients still get the JSON error.
 */
@Configuration
public class ErrorPageConfig {

  @Bean
  public ErrorViewResolver brandedErrorViewResolver() {
    return (request, status, model) -> {
      View view =
          (viewModel, req, response) -> {
            response.setContentType(ErrorPage.CONTENT_TYPE.toString());
            response.getWriter().write(ErrorPage.render(status.value()));
          };
      ModelAndView page = new ModelAndView(view, model);
      page.setStatus(status);
      return page;
    };
  }
}
