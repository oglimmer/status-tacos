/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

/**
 * Builds the alert email: the same information as the Teams card, as a multipart message with an
 * HTML part and a plain text part. The plain text part has only ASCII characters, so every mail
 * client shows it correctly.
 */
@Service
public class EmailNotificationService {

  private static final String DOWN_COLOR = "#d2412b";
  private static final String UP_COLOR = "#2e8b47";
  private static final String TEST_COLOR = "#33ada4";
  private static final String TEXT_COLOR = "#102e2a";
  private static final String MUTED_COLOR = "#3d5a55";
  private static final String FONT =
      "-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif";

  private final String appUrl;

  public EmailNotificationService(@Value("${monitor.app-url:}") String appUrl) {
    this.appUrl = appUrl == null ? "" : appUrl.replaceAll("/+$", "");
  }

  /** Builds the message and gives it to the mail server. */
  public void send(JavaMailSender mailSender, String from, String to, TeamsAlert alert)
      throws MessagingException {
    MimeMessage message = mailSender.createMimeMessage();
    MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
    helper.setFrom(from);
    helper.setTo(to);
    helper.setSubject(subject(alert));
    helper.setText(plainText(alert), html(alert));
    mailSender.send(message);
  }

  static String subject(TeamsAlert alert) {
    return switch (alert.kind()) {
      case DOWN -> "Service is DOWN: " + alert.monitorName();
      case UP -> "Service is UP: " + alert.monitorName();
      case TEST -> "Test notification: " + alert.contactName();
    };
  }

  private static String headline(TeamsAlert alert) {
    return switch (alert.kind()) {
      case DOWN -> "DOWN: " + alert.monitorName();
      case UP -> "UP again: " + alert.monitorName();
      case TEST -> "Test notification: " + alert.contactName();
    };
  }

  private static String color(TeamsAlert.Kind kind) {
    return switch (kind) {
      case DOWN -> DOWN_COLOR;
      case UP -> UP_COLOR;
      case TEST -> TEST_COLOR;
    };
  }

  private static String badge(TeamsAlert.Kind kind) {
    return switch (kind) {
      case DOWN -> "SERVICE DOWN";
      case UP -> "SERVICE UP";
      case TEST -> "TEST";
    };
  }

  String plainText(TeamsAlert alert) {
    StringBuilder text = new StringBuilder();
    String headline = headline(alert);
    text.append(headline).append('\n');
    text.append("=".repeat(Math.min(headline.length(), 72))).append("\n\n");
    text.append(TeamsNotificationService.summary(alert)).append("\n\n");

    List<Map<String, String>> facts = TeamsNotificationService.facts(alert);
    int width = facts.stream().mapToInt(f -> f.get("title").length()).max().orElse(0) + 1;
    for (Map<String, String> fact : facts) {
      String title = fact.get("title") + ":";
      text.append(title).append(" ".repeat(width + 1 - title.length()));
      text.append(fact.get("value")).append('\n');
    }

    List<String[]> links = links(alert);
    if (!links.isEmpty()) {
      text.append('\n');
      for (String[] link : links) {
        text.append(link[0]).append(": ").append(link[1]).append('\n');
      }
    }
    text.append("\n-- \nSent by Status Tacos\n");
    return toAscii(text.toString());
  }

  String html(TeamsAlert alert) {
    String color = color(alert.kind());
    StringBuilder rows = new StringBuilder();
    List<Map<String, String>> facts = TeamsNotificationService.facts(alert);
    for (int i = 0; i < facts.size(); i++) {
      Map<String, String> fact = facts.get(i);
      String border = i == facts.size() - 1 ? "" : "border-bottom:1px solid #e3ebe8;";
      rows.append("<tr>")
          .append("<td style=\"padding:10px 12px 10px 0;")
          .append(border)
          .append("color:")
          .append(MUTED_COLOR)
          .append(";font-size:13px;font-weight:600;white-space:nowrap;vertical-align:top;")
          .append("width:1%;\">")
          .append(esc(fact.get("title")))
          .append("</td><td style=\"padding:10px 0;")
          .append(border)
          .append("color:")
          .append(TEXT_COLOR)
          .append(";font-size:14px;vertical-align:top;word-break:break-word;\">")
          .append(factValue(fact))
          .append("</td></tr>");
    }

    StringBuilder buttons = new StringBuilder();
    for (String[] link : links(alert)) {
      boolean primary = buttons.isEmpty();
      buttons
          .append("<a href=\"")
          .append(esc(link[1]))
          .append("\" style=\"display:inline-block;margin:0 8px 8px 0;padding:11px 18px;")
          .append("border-radius:8px;font-size:14px;font-weight:600;text-decoration:none;")
          .append(
              primary
                  ? "background:" + color + ";color:#ffffff;border:1px solid " + color + ";"
                  : "background:#ffffff;color:" + TEXT_COLOR + ";border:1px solid #c9d6d2;")
          .append("\">")
          .append(esc(link[0]))
          .append("</a>");
    }

    return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\">"
        + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
        + "<meta name=\"color-scheme\" content=\"light\">"
        + "<title>"
        + esc(subject(alert))
        + "</title></head>"
        + "<body style=\"margin:0;padding:0;background:#e9f2ee;font-family:"
        + FONT
        + ";\">"
        // Preheader: the preview line in the inbox list
        + "<div style=\"display:none;max-height:0;overflow:hidden;\">"
        + esc(TeamsNotificationService.summary(alert))
        + "</div>"
        + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\""
        + " style=\"background:#e9f2ee;\"><tr><td align=\"center\" style=\"padding:32px 16px;\">"
        + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\""
        + " style=\"max-width:600px;\">"
        // Brand line
        + "<tr><td style=\"padding:0 4px 12px;font-size:15px;font-weight:800;color:"
        + TEXT_COLOR
        + ";letter-spacing:.3px;\">&#127790; Status Tacos</td></tr>"
        // Card
        + "<tr><td style=\"background:#ffffff;border-radius:14px;overflow:hidden;"
        + "border:1px solid #d5e2de;\">"
        + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\">"
        + "<tr><td style=\"height:6px;background:"
        + color
        + ";font-size:0;line-height:0;\">&nbsp;</td></tr>"
        + "<tr><td style=\"padding:28px 28px 8px;\">"
        + "<span style=\"display:inline-block;padding:4px 10px;border-radius:999px;background:"
        + color
        + ";color:#ffffff;font-size:11px;font-weight:700;letter-spacing:.8px;\">"
        + badge(alert.kind())
        + "</span>"
        + "<h1 style=\"margin:14px 0 6px;font-size:22px;line-height:1.3;color:"
        + TEXT_COLOR
        + ";\">"
        + esc(headline(alert))
        + "</h1>"
        + "<p style=\"margin:0;font-size:15px;line-height:1.5;color:"
        + MUTED_COLOR
        + ";\">"
        + esc(TeamsNotificationService.summary(alert))
        + "</p></td></tr>"
        + "<tr><td style=\"padding:16px 28px 8px;\">"
        + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\">"
        + rows
        + "</table></td></tr>"
        + (buttons.isEmpty()
            ? ""
            : "<tr><td style=\"padding:16px 28px 20px;\">" + buttons + "</td></tr>")
        + "</table></td></tr>"
        // Footer
        + "<tr><td style=\"padding:16px 4px 0;font-size:12px;line-height:1.5;color:"
        + MUTED_COLOR
        + ";\">You get this email because this address is an alert contact in Status Tacos."
        + "</td></tr>"
        + "</table></td></tr></table></body></html>";
  }

  private static String factValue(Map<String, String> fact) {
    String value = fact.get("value");
    if ("URL".equals(fact.get("title")) && isHttpUrl(value)) {
      return "<a href=\""
          + esc(value)
          + "\" style=\"color:"
          + TEXT_COLOR
          + ";text-decoration:underline;\">"
          + esc(value)
          + "</a>";
    }
    if ("Reason".equals(fact.get("title"))) {
      return "<code style=\"font-family:ui-monospace,Menlo,Consolas,monospace;font-size:13px;"
          + "background:#f4f7f6;padding:2px 6px;border-radius:4px;\">"
          + esc(value)
          + "</code>";
    }
    return esc(value);
  }

  /** The same buttons as the Teams card: the monitored URL and Status Tacos. */
  private List<String[]> links(TeamsAlert alert) {
    List<String[]> links = new ArrayList<>();
    if (alert.kind() != TeamsAlert.Kind.TEST && isHttpUrl(alert.monitorUrl())) {
      links.add(new String[] {"Open monitored URL", alert.monitorUrl()});
    }
    if (isHttpUrl(appUrl)) {
      links.add(new String[] {"Open Status Tacos", appUrl + "/"});
    }
    return links;
  }

  private static boolean isHttpUrl(String url) {
    return url != null && url.matches("^https?://.+");
  }

  private static String esc(String text) {
    return HtmlUtils.htmlEscape(text == null ? "" : text, "UTF-8");
  }

  /** Removes accents ("é" becomes "e") and replaces all other non-ASCII characters with "?". */
  static String toAscii(String text) {
    String replaced =
        text.replace("…", "...")
            .replace("–", "-")
            .replace("—", "-")
            .replace("‘", "'")
            .replace("’", "'")
            .replace("“", "\"")
            .replace("”", "\"")
            .replace("ß", "ss")
            .replace("ä", "ae")
            .replace("ö", "oe")
            .replace("ü", "ue")
            .replace("Ä", "Ae")
            .replace("Ö", "Oe")
            .replace("Ü", "Ue");
    String decomposed =
        Normalizer.normalize(replaced, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    return decomposed.replaceAll("[^\\x00-\\x7F]", "?");
  }
}
