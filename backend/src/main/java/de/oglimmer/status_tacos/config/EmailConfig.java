/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "monitor.email")
public class EmailConfig {

  private boolean enabled = false;
  private String from = "noreply@status-tacos.local";
  private String subjectPrefix = "[Status Tacos]";

  private Smtp smtp = new Smtp();
  private Template template = new Template();

  @Data
  public static class Smtp {
    private String host = "localhost";
    private int port = 587;
    private String username = "";
    private String password = "";
    private boolean auth = true;
    private Starttls starttls = new Starttls();

    @Data
    public static class Starttls {
      private boolean enable = true;
      private boolean required = false;
    }
  }

  @Data
  public static class Template {
    private String monitorDown = "Monitor '%s' is DOWN - Status: %d";
    private String monitorUp = "Monitor '%s' is UP again";
  }
}
