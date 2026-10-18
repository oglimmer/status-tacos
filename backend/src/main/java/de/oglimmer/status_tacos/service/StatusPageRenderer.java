/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.MustacheException;
import com.samskivert.mustache.Template;
import de.oglimmer.status_tacos.dto.PublicMonitorStatusDto;
import de.oglimmer.status_tacos.dto.PublicStatusPageDto;
import de.oglimmer.status_tacos.dto.StatusType;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.jsoup.safety.Safelist;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Renders the public status page of a tenant: a Mustache template of the body, plus CSS.
 *
 * <p>The tenant writes both, and anyone can open the page, so the page must never run code:
 *
 * <ul>
 *   <li>Mustache has no logic and no method calls. The data is plain maps, and all values are
 *       HTML-escaped.
 *   <li>The filled template is cleaned with an allow-list ({@link #SAFELIST}): no scripts, no event
 *       handlers, no forms, no frames, only http(s) and mailto links.
 *   <li>The CSS goes into one {@code <style>} element and must not contain {@code <}, so it cannot
 *       close the element.
 *   <li>The server sends the page with {@link #CONTENT_SECURITY_POLICY}: no scripts, and a sandbox,
 *       so the page has an opaque origin and cannot read anything of the app, even if a bug in the
 *       steps above lets something through.
 * </ul>
 *
 * <p>The base CSS is in the cascade layer {@code base}, the CSS of the tenant in the layer {@code
 * tenant}. A later layer always wins, so tenant CSS needs no {@code !important} or long selectors.
 */
@Component
public class StatusPageRenderer {

  public static final int MAX_TEMPLATE_LENGTH = 64 * 1024;
  public static final int MAX_CSS_LENGTH = 64 * 1024;

  /** The page refreshes itself; it cannot use scripts. The data is cached for 30 seconds. */
  static final int REFRESH_SECONDS = 60;

  /** Images and fonts from any https URL (for logos and brand fonts), nothing else from outside. */
  private static final String POLICY =
      "default-src 'none'; style-src 'unsafe-inline'; img-src https: data:; font-src https: data:;"
          + " base-uri 'none'; form-action 'none'";

  /** Header of the public page. Any site may embed the page in a frame. */
  public static final String CONTENT_SECURITY_POLICY =
      POLICY
          + "; frame-ancestors *; sandbox allow-popups allow-popups-to-escape-sandbox"
          + " allow-top-navigation-by-user-activation";

  private static final Safelist SAFELIST = buildSafelist();

  private static final PublicStatusPageDto SAMPLE_PAGE =
      new PublicStatusPageDto(
          "Sample",
          List.of(
              new PublicMonitorStatusDto(
                  "Sample", StatusType.down, LocalDateTime.of(2026, 1, 1, 0, 0), BigDecimal.TEN)));

  private static final DateTimeFormatter DISPLAY_TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'");

  private final Mustache.Compiler compiler =
      Mustache.compiler()
          .defaultValue("")
          .emptyStringIsFalse(true)
          .withLoader(
              name -> {
                throw new IllegalArgumentException("Partials are not supported: " + name);
              });

  private final Clock clock;
  @Getter private final String defaultTemplate;
  @Getter private final String baseCss;
  @Getter private final String starterCss;
  private final Template compiledDefault;

  public StatusPageRenderer(Clock clock) {
    this.clock = clock;
    this.defaultTemplate = resource("status-page/default.html");
    this.baseCss = resource("status-page/base.css");
    this.starterCss = resource("status-page/starter.css");
    this.compiledDefault = compiler.compile(defaultTemplate);
  }

  /** A full HTML document. The cleaning removed {@code removed}: tags and attributes. */
  public record Rendered(String html, Set<String> removed) {}

  /**
   * @param htmlTemplate null for the default template
   * @param css null for no tenant CSS
   * @param refresh true for the public page, false for the preview in the editor
   * @throws IllegalArgumentException with a message for the user if the template or CSS is invalid
   */
  public Rendered render(
      PublicStatusPageDto page, String htmlTemplate, String css, boolean refresh) {
    validateCss(css);
    Template template = htmlTemplate == null ? compiledDefault : compile(htmlTemplate);
    String body = execute(template, page);
    String cleanBody =
        Jsoup.clean(body, "", SAFELIST, new Document.OutputSettings().prettyPrint(false));
    return new Rendered(
        document(page.getTenantName(), cleanBody, css, refresh), findRemoved(body, cleanBody));
  }

  /**
   * Checks a template and CSS before they are saved.
   *
   * @throws IllegalArgumentException with a message for the user
   */
  public void validate(String htmlTemplate, String css) {
    validateCss(css);
    if (htmlTemplate != null) {
      if (htmlTemplate.length() > MAX_TEMPLATE_LENGTH) {
        throw new IllegalArgumentException(
            "The template must not be longer than " + MAX_TEMPLATE_LENGTH + " characters.");
      }
      // Some errors (for example a partial) show only when the template runs.
      execute(compile(htmlTemplate), SAMPLE_PAGE);
    }
  }

  private String execute(Template template, PublicStatusPageDto page) {
    try {
      return template.execute(context(page));
    } catch (MustacheException | IllegalArgumentException e) {
      throw new IllegalArgumentException("The template is invalid: " + e.getMessage(), e);
    }
  }

  private Template compile(String htmlTemplate) {
    try {
      return compiler.compile(htmlTemplate);
    } catch (MustacheException | IllegalArgumentException e) {
      throw new IllegalArgumentException("The template is invalid: " + e.getMessage(), e);
    }
  }

  private static void validateCss(String css) {
    if (css == null) {
      return;
    }
    if (css.length() > MAX_CSS_LENGTH) {
      throw new IllegalArgumentException(
          "The CSS must not be longer than " + MAX_CSS_LENGTH + " characters.");
    }
    if (css.indexOf('<') >= 0) {
      throw new IllegalArgumentException("The CSS must not contain the character \"<\".");
    }
  }

  private String document(String tenantName, String body, String css, boolean refresh) {
    StringBuilder html = new StringBuilder(body.length() + baseCss.length() + 1024);
    html.append("<!doctype html>\n<html lang=\"en\">\n<head>\n")
        .append("<meta charset=\"utf-8\">\n")
        .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
        // Also in the document: the preview in the editor has no response headers.
        .append("<meta http-equiv=\"Content-Security-Policy\" content=\"")
        .append(POLICY)
        .append("\">\n");
    if (refresh) {
      html.append("<meta http-equiv=\"refresh\" content=\"")
          .append(REFRESH_SECONDS)
          .append("\">\n");
    }
    html.append("<title>")
        .append(Entities.escape(tenantName == null ? "" : tenantName))
        .append(" status</title>\n<style>\n@layer base, tenant;\n@layer base {\n")
        .append(baseCss)
        .append("\n}\n@layer tenant {\n")
        .append(css == null ? "" : css)
        .append("\n}\n</style>\n</head>\n<body>\n")
        .append(body)
        .append("\n</body>\n</html>\n");
    return html.toString();
  }

  /** The data of the template: maps and lists only, so a template cannot call methods. */
  private Map<String, Object> context(PublicStatusPageDto page) {
    LocalDateTime now = LocalDateTime.now(clock);
    List<PublicMonitorStatusDto> monitors = page.getMonitors();
    long downCount = monitors.stream().filter(m -> m.getCurrentStatus() == StatusType.down).count();

    Map<String, Object> ctx = new HashMap<>();
    ctx.put("tenantName", page.getTenantName());
    ctx.put("monitors", monitors.stream().map(m -> monitorContext(m, now)).toList());
    ctx.put("hasMonitors", !monitors.isEmpty());
    ctx.put("monitorCount", monitors.size());
    ctx.put("downCount", downCount);
    ctx.put("anyDown", downCount > 0);
    ctx.put("allUp", downCount == 0);
    ctx.put("headline", headline(downCount));
    ctx.put("updatedAt", DISPLAY_TIME.format(now));
    ctx.put("updatedAtIso", iso(now));
    return ctx;
  }

  private static Map<String, Object> monitorContext(PublicMonitorStatusDto m, LocalDateTime now) {
    String status = m.getCurrentStatus() == null ? "unknown" : m.getCurrentStatus().name();
    BigDecimal uptime = m.getUptimePercentage90d();
    Map<String, Object> ctx = new HashMap<>();
    ctx.put("name", m.getName());
    ctx.put("status", status);
    ctx.put("statusLabel", statusLabel(status));
    ctx.put("isUp", "up".equals(status));
    ctx.put("isDown", "down".equals(status));
    ctx.put("isUnknown", "unknown".equals(status));
    if (m.getDownSince() != null) {
      ctx.put("downSince", DISPLAY_TIME.format(m.getDownSince()));
      ctx.put("downSinceIso", iso(m.getDownSince()));
      ctx.put("downFor", duration(Duration.between(m.getDownSince(), now)));
    }
    ctx.put("uptime90d", uptime == null ? "N/A" : uptime.toPlainString() + "%");
    ctx.put("uptimeValue", uptime == null ? "" : uptime.toPlainString());
    ctx.put("uptimeLevel", uptimeLevel(uptime));
    return ctx;
  }

  private static String headline(long downCount) {
    if (downCount == 0) {
      return "All systems up";
    }
    return downCount == 1 ? "1 system down" : downCount + " systems down";
  }

  private static String statusLabel(String status) {
    return switch (status) {
      case "up" -> "Up";
      case "down" -> "Down";
      default -> "No data";
    };
  }

  /** The same levels as the dashboard. */
  private static String uptimeLevel(BigDecimal uptime) {
    if (uptime == null) {
      return "no-data";
    }
    double value = uptime.doubleValue();
    if (value >= 99) {
      return "excellent";
    }
    if (value >= 95) {
      return "good";
    }
    return value >= 90 ? "warning" : "poor";
  }

  static String duration(Duration duration) {
    long minutes = Math.max(0, duration.toMinutes());
    long days = minutes / 1440;
    long hours = minutes % 1440 / 60;
    if (days > 0) {
      return days + "d " + hours + "h";
    }
    return hours > 0 ? hours + "h " + minutes % 60 + "m" : minutes % 60 + "m";
  }

  private static String iso(LocalDateTime time) {
    return time.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
  }

  /**
   * Tags and attributes that the cleaning removed, so the editor can tell the user. A rough list:
   * an attribute that is kept on one element of a tag and removed on another is not in it.
   */
  private static Set<String> findRemoved(String body, String cleanBody) {
    Set<String> kept = new HashSet<>();
    for (Element el : Jsoup.parseBodyFragment(cleanBody).body().getAllElements()) {
      kept.add("<" + el.tagName() + ">");
      for (Attribute attr : el.attributes()) {
        kept.add(el.tagName() + " " + attr.getKey());
      }
    }
    Set<String> removed = new LinkedHashSet<>();
    for (Element el : Jsoup.parseBodyFragment(body).body().getAllElements()) {
      if (el == el.ownerDocument().body()) {
        continue;
      }
      String tag = "<" + el.tagName() + ">";
      if (!kept.contains(tag)) {
        removed.add(tag);
        continue;
      }
      for (Attribute attr : el.attributes()) {
        if (!kept.contains(el.tagName() + " " + attr.getKey())) {
          removed.add(attr.getKey() + " on <" + el.tagName() + ">");
        }
      }
    }
    return removed;
  }

  private static Safelist buildSafelist() {
    return new Safelist()
        .addTags(
            "a",
            "abbr",
            "article",
            "aside",
            "b",
            "blockquote",
            "br",
            "caption",
            "code",
            "dd",
            "del",
            "details",
            "div",
            "dl",
            "dt",
            "em",
            "figcaption",
            "figure",
            "footer",
            "h1",
            "h2",
            "h3",
            "h4",
            "h5",
            "h6",
            "header",
            "hr",
            "i",
            "img",
            "ins",
            "kbd",
            "li",
            "main",
            "mark",
            "nav",
            "ol",
            "p",
            "pre",
            "q",
            "s",
            "section",
            "small",
            "span",
            "strong",
            "sub",
            "summary",
            "sup",
            "table",
            "tbody",
            "td",
            "tfoot",
            "th",
            "thead",
            "time",
            "tr",
            "u",
            "ul")
        .addAttributes(
            ":all",
            "class",
            "id",
            "style",
            "title",
            "lang",
            "dir",
            "role",
            "aria-label",
            "aria-hidden",
            "aria-live",
            "aria-describedby",
            "aria-labelledby")
        .addAttributes("a", "href", "target", "rel")
        .addAttributes("img", "src", "alt", "width", "height", "loading")
        .addAttributes("time", "datetime")
        .addAttributes("td", "colspan", "rowspan", "headers")
        .addAttributes("th", "colspan", "rowspan", "scope", "headers")
        .addAttributes("details", "open")
        .addAttributes("ol", "start", "reversed")
        .addProtocols("a", "href", "http", "https", "mailto", "#")
        .addProtocols("img", "src", "https", "data");
  }

  private static String resource(String path) {
    try {
      return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Missing resource " + path, e);
    }
  }
}
