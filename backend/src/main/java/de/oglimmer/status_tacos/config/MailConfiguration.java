/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.config;

import java.util.Properties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration
@ConditionalOnProperty(prefix = "monitor.email", name = "enabled", havingValue = "true")
public class MailConfiguration {

  private final EmailConfig emailConfig;

  public MailConfiguration(EmailConfig emailConfig) {
    this.emailConfig = emailConfig;
  }

  @Bean
  public JavaMailSender javaMailSender() {
    JavaMailSenderImpl mailSender = new JavaMailSenderImpl();

    mailSender.setHost(emailConfig.getSmtp().getHost());
    mailSender.setPort(emailConfig.getSmtp().getPort());
    mailSender.setUsername(emailConfig.getSmtp().getUsername());
    mailSender.setPassword(emailConfig.getSmtp().getPassword());

    Properties props = mailSender.getJavaMailProperties();
    props.put("mail.transport.protocol", "smtp");
    props.put("mail.smtp.auth", emailConfig.getSmtp().isAuth());
    props.put("mail.smtp.starttls.enable", emailConfig.getSmtp().getStarttls().isEnable());
    props.put("mail.smtp.starttls.required", emailConfig.getSmtp().getStarttls().isRequired());
    props.put("mail.debug", "false");

    return mailSender;
  }
}
