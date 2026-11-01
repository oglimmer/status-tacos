/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import de.oglimmer.status_tacos.persistence.AlertChannelHealth;
import de.oglimmer.status_tacos.persistence.AlertChannelHealth.Channel;
import de.oglimmer.status_tacos.repository.AlertChannelHealthRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Whether this server can send alerts. Only failures that are our fault count:
 *
 * <ul>
 *   <li>EMAIL and PUSH: the mail server and APNs are ours. 3 failed sends in a row: failing.
 *   <li>WEBHOOK (HTTP and Teams): one broken webhook is the problem of its owner. Failing only when
 *       2 or more different hosts failed in a row, without a successful send in between.
 * </ul>
 *
 * One successful send makes the channel healthy again. When this server cannot alert, the heartbeat
 * tells the other server, and it alerts about this server.
 *
 * <p>Each call has its own transaction: it commits at once, so the locked row is not held while the
 * alert thread sends the next alert.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertDeliveryHealthService {

  static final int MAX_FAILURES_OWN_CHANNEL = 3;
  static final int MIN_FAILED_WEBHOOK_HOSTS = 2;
  private static final int MAX_REASON_LENGTH = 500;
  private static final int MAX_HOSTS_LENGTH = 1000;

  private final AlertChannelHealthRepository repository;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  /** The state for the heartbeat. */
  public record Health(boolean canAlert, LocalDateTime since, String reason) {}

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordSuccess(Channel channel) {
    AlertChannelHealth health = load(channel);
    if (health.getConsecutiveFailures() == 0 && health.getFailingSince() == null) {
      return; // the usual case: nothing to write
    }
    boolean wasFailing = health.getFailingSince() != null;
    health.setConsecutiveFailures(0);
    health.setFailedHosts(null);
    health.setFailingSince(null);
    health.setReason(null);
    repository.save(health);
    if (wasFailing) {
      log.info("Alert channel {} works again", channel);
      eventPublisher.publishEvent(new AlertDeliveryHealthChanged(true));
    }
  }

  /**
   * @param host the host that failed (the mail server, APNs or the webhook host)
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordFailure(Channel channel, String host, String reason) {
    AlertChannelHealth health = load(channel);
    health.setConsecutiveFailures(health.getConsecutiveFailures() + 1);
    Set<String> hosts = hosts(health.getFailedHosts());
    if (host != null && !host.isBlank()) {
      hosts.add(host.toLowerCase());
    }
    health.setFailedHosts(join(hosts));
    health.setReason(truncate(channel + ": " + reason));

    boolean failing =
        channel == Channel.WEBHOOK
            ? hosts.size() >= MIN_FAILED_WEBHOOK_HOSTS
            : health.getConsecutiveFailures() >= MAX_FAILURES_OWN_CHANNEL;
    boolean becameFailing = failing && health.getFailingSince() == null;
    if (becameFailing) {
      health.setFailingSince(LocalDateTime.now(clock));
    }
    repository.save(health);
    if (becameFailing) {
      log.error("Alert channel {} is failing, this server cannot alert: {}", channel, reason);
      eventPublisher.publishEvent(new AlertDeliveryHealthChanged(false));
    }
  }

  @Transactional(readOnly = true)
  public Health health() {
    List<AlertChannelHealth> failing =
        repository.findAll().stream().filter(h -> h.getFailingSince() != null).toList();
    if (failing.isEmpty()) {
      return new Health(true, null, null);
    }
    LocalDateTime since =
        failing.stream()
            .map(AlertChannelHealth::getFailingSince)
            .min(Comparator.naturalOrder())
            .orElse(null);
    String reason =
        failing.stream()
            .map(AlertChannelHealth::getReason)
            .filter(Objects::nonNull)
            .collect(Collectors.joining("; "));
    return new Health(false, since, reason);
  }

  private AlertChannelHealth load(Channel channel) {
    // The migration inserts the rows. Without them (tests on H2) the first call creates the row.
    return repository
        .findForUpdate(channel)
        .orElseGet(() -> AlertChannelHealth.builder().channel(channel).build());
  }

  private static Set<String> hosts(String value) {
    Set<String> hosts = new LinkedHashSet<>();
    if (value != null && !value.isBlank()) {
      for (String host : value.split(",")) {
        hosts.add(host);
      }
    }
    return hosts;
  }

  private static String join(Set<String> hosts) {
    if (hosts.isEmpty()) {
      return null;
    }
    String joined = String.join(",", hosts);
    // Only the number of hosts matters; keep the column short
    return joined.length() <= MAX_HOSTS_LENGTH ? joined : joined.substring(0, MAX_HOSTS_LENGTH);
  }

  private static String truncate(String text) {
    return text.length() <= MAX_REASON_LENGTH ? text : text.substring(0, MAX_REASON_LENGTH);
  }
}
