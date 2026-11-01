/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.controller;

import de.oglimmer.status_tacos.config.PeerConfig;
import de.oglimmer.status_tacos.dto.PeerStatusDto;
import de.oglimmer.status_tacos.service.AlertDeliveryHealthService;
import de.oglimmer.status_tacos.service.PeerService;
import de.oglimmer.status_tacos.service.UserTenantResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** The peer setup of this server, for the dashboard of the members of the peer tenant. */
@RestController
@RequestMapping("/v1/peer")
@RequiredArgsConstructor
public class PeerStatusController {

  private final PeerConfig peerConfig;
  private final PeerService peerService;
  private final AlertDeliveryHealthService deliveryHealth;
  private final UserTenantResolver userTenantResolver;

  /** 404 when the servers do not work together or the user is no member of the peer tenant. */
  @GetMapping("/status")
  public PeerStatusDto status() {
    if (!peerConfig.isEnabled()
        || !userTenantResolver.getCurrentUserTenantIds().contains(peerConfig.getTenantId())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No peer setup");
    }
    PeerService.PeerSetup setup = peerService.setup();
    AlertDeliveryHealthService.Health health = deliveryHealth.health();
    return new PeerStatusDto(
        setup.state().name(),
        peerConfig.getTenantId(),
        setup.monitor() != null ? setup.monitor().getId() : null,
        setup.monitor() != null ? setup.monitor().getName() : null,
        health.canAlert(),
        health.since(),
        health.reason());
  }
}
