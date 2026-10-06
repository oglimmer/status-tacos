/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "alert_contacts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertContact {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @ManyToOne
  @JoinColumn(name = "tenant_id", nullable = false)
  private Tenant tenant;

  @Column(name = "tenant_id", insertable = false, updatable = false)
  private Integer tenantId;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, length = 20)
  private AlertContactType type;

  @NotBlank
  @Size(max = 2048)
  @Column(name = "`value`", nullable = false, length = 2048)
  private String value;

  @Size(max = 100)
  @Column(name = "name", length = 100)
  private String name;

  @Column(name = "is_active", nullable = false)
  @Builder.Default
  private boolean isActive = true;

  @Column(name = "http_method", length = 10)
  private String httpMethod;

  @Column(name = "http_headers", columnDefinition = "TEXT")
  private String httpHeaders;

  @Column(name = "http_body", columnDefinition = "TEXT")
  private String httpBody;

  @Column(name = "http_content_type", length = 50)
  private String httpContentType;

  /** true: alerted for every monitor of the tenant. false: only for {@link #monitors}. */
  @Column(name = "all_monitors", nullable = false)
  @Builder.Default
  private boolean allMonitors = true;

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "alert_contact_monitors",
      joinColumns = @JoinColumn(name = "alert_contact_id"),
      inverseJoinColumns = @JoinColumn(name = "monitor_id"))
  @Builder.Default
  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  private Set<Monitor> monitors = new HashSet<>();

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @PrePersist
  protected void onCreate() {
    LocalDateTime now = LocalDateTime.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = LocalDateTime.now();
  }

  public enum AlertContactType {
    EMAIL,
    HTTP,
    TEAMS
  }

  public void validateValue() {
    if (type == AlertContactType.EMAIL && !isValidEmail(value)) {
      throw new IllegalArgumentException("Invalid email format for EMAIL type contact");
    }
    if (type == AlertContactType.HTTP && !isValidUrl(value)) {
      throw new IllegalArgumentException("Invalid URL format for HTTP type contact");
    }
    if (type == AlertContactType.TEAMS && !isValidHttpsUrl(value)) {
      throw new IllegalArgumentException("TEAMS contact needs an https:// workflow URL");
    }
    if (type == AlertContactType.HTTP
        && httpMethod != null
        && !httpMethod.matches("(?i)^(GET|POST)$")) {
      throw new IllegalArgumentException("HTTP method must be GET or POST");
    }
  }

  private boolean isValidEmail(String email) {
    return email != null && email.matches("^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$");
  }

  private boolean isValidUrl(String url) {
    return url != null && url.matches("^https?://.*");
  }

  private boolean isValidHttpsUrl(String url) {
    return url != null && url.matches("^https://.*");
  }

  public Map<String, String> getHttpHeadersMap() {
    if (httpHeaders == null || httpHeaders.trim().isEmpty()) {
      return Map.of();
    }
    try {
      ObjectMapper mapper = new ObjectMapper();
      return mapper.readValue(httpHeaders, Map.class);
    } catch (JsonProcessingException e) {
      return Map.of();
    }
  }

  public void setHttpHeadersFromMap(Map<String, String> headers) {
    if (headers == null || headers.isEmpty()) {
      this.httpHeaders = null;
      return;
    }
    try {
      ObjectMapper mapper = new ObjectMapper();
      this.httpHeaders = mapper.writeValueAsString(headers);
    } catch (JsonProcessingException e) {
      this.httpHeaders = null;
    }
  }
}
