/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import de.oglimmer.status_tacos.persistence.AlertChannelHealth;
import de.oglimmer.status_tacos.persistence.AlertChannelHealth.Channel;
import de.oglimmer.status_tacos.repository.AlertChannelHealthRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class AlertDeliveryHealthServiceTest {

  @Mock private AlertChannelHealthRepository repository;
  @Mock private ApplicationEventPublisher eventPublisher;

  private final Map<Channel, AlertChannelHealth> rows = new EnumMap<>(Channel.class);
  private AlertDeliveryHealthService service;

  @BeforeEach
  void setUp() {
    lenient()
        .when(repository.findForUpdate(any()))
        .thenAnswer(inv -> Optional.ofNullable(rows.get(inv.<Channel>getArgument(0))));
    lenient()
        .when(repository.save(any()))
        .thenAnswer(
            inv -> {
              AlertChannelHealth h = inv.getArgument(0);
              rows.put(h.getChannel(), h);
              return h;
            });
    lenient().when(repository.findAll()).thenAnswer(inv -> List.copyOf(rows.values()));
    service =
        new AlertDeliveryHealthService(
            repository,
            eventPublisher,
            Clock.fixed(Instant.parse("2026-11-01T10:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void emailFailsThreeTimesInARow_cannotAlert() {
    service.recordFailure(Channel.EMAIL, "smtp.local", "connection refused");
    service.recordFailure(Channel.EMAIL, "smtp.local", "connection refused");
    assertThat(service.health().canAlert()).isTrue();

    service.recordFailure(Channel.EMAIL, "smtp.local", "connection refused");

    AlertDeliveryHealthService.Health health = service.health();
    assertThat(health.canAlert()).isFalse();
    assertThat(health.reason()).contains("EMAIL").contains("connection refused");
    assertThat(health.since()).isNotNull();
    verify(eventPublisher, times(1)).publishEvent(new AlertDeliveryHealthChanged(false));
  }

  @Test
  void oneSuccess_healthyAgain() {
    for (int i = 0; i < 3; i++) {
      service.recordFailure(Channel.PUSH, "apns", "timeout");
    }
    service.recordSuccess(Channel.PUSH);

    assertThat(service.health().canAlert()).isTrue();
    verify(eventPublisher).publishEvent(new AlertDeliveryHealthChanged(true));
  }

  @Test
  void successBetweenFailures_countStartsAgain() {
    service.recordFailure(Channel.EMAIL, "smtp.local", "x");
    service.recordFailure(Channel.EMAIL, "smtp.local", "x");
    service.recordSuccess(Channel.EMAIL);
    service.recordFailure(Channel.EMAIL, "smtp.local", "x");

    assertThat(service.health().canAlert()).isTrue();
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  void oneBrokenWebhook_isNotOurProblem() {
    for (int i = 0; i < 10; i++) {
      service.recordFailure(Channel.WEBHOOK, "hooks.customer.example", "500");
    }

    assertThat(service.health().canAlert()).isTrue();
  }

  @Test
  void twoDifferentWebhookHostsFail_cannotAlert() {
    service.recordFailure(Channel.WEBHOOK, "hooks.a.example", "timeout");
    service.recordFailure(Channel.WEBHOOK, "hooks.b.example", "timeout");

    assertThat(service.health().canAlert()).isFalse();
  }

  @Test
  void successWithoutFailures_writesNothing() {
    service.recordSuccess(Channel.EMAIL);

    verify(repository, never()).save(any());
  }
}
