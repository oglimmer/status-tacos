/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import de.oglimmer.status_tacos.config.ApnsConfig;
import de.oglimmer.status_tacos.persistence.PushDevice;
import de.oglimmer.status_tacos.persistence.User;
import de.oglimmer.status_tacos.repository.PushDeviceRepository;
import java.time.LocalDateTime;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The iOS devices of the users and the delivery of push notifications to them. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationService {

  /** APNs tokens are hex. Today 32 bytes; Apple says the length can change. */
  private static final Pattern TOKEN = Pattern.compile("^[0-9a-fA-F]{64,200}$");

  private final ApnsConfig apnsConfig;
  private final ApnsClient apnsClient;
  private final PushDeviceRepository deviceRepository;

  public boolean isEnabled() {
    return apnsConfig.isEnabled();
  }

  /**
   * Saves the device token of the app for the user. A token is unique: when another user signs in
   * on the same device, the token moves to that user.
   */
  @Transactional
  public PushDevice registerDevice(
      User user, String token, PushDevice.Environment environment, String deviceName) {
    if (token == null || !TOKEN.matcher(token).matches()) {
      throw new IllegalArgumentException("Invalid device token");
    }
    if (environment == null) {
      throw new IllegalArgumentException("Environment is missing");
    }
    String normalized = token.toLowerCase();
    PushDevice device =
        deviceRepository
            .findByToken(normalized)
            .orElseGet(() -> PushDevice.builder().token(normalized).build());
    device.setUser(user);
    device.setEnvironment(environment);
    device.setDeviceName(truncate(deviceName, 100));
    device.setLastSeenAt(LocalDateTime.now());
    return deviceRepository.save(device);
  }

  /** Removes the token, for example when the user signs out in the app. */
  @Transactional
  public boolean unregisterDevice(User user, String token) {
    return token != null
        && deviceRepository.deleteByTokenAndUserId(token.toLowerCase(), user.getId()) > 0;
  }

  @Transactional(readOnly = true)
  public int deviceCount(User user) {
    return deviceRepository.findByUserId(user.getId()).size();
  }

  /**
   * Sends the message to every device of the user. Forgets devices that APNs reports as gone.
   *
   * @return the number of devices that got the message
   */
  @Transactional
  public int sendToUser(User user, PushMessage message) {
    if (!apnsConfig.isEnabled()) {
      log.warn("APNs is not enabled: no push notification for user {}", user.getId());
      return 0;
    }
    int delivered = 0;
    for (PushDevice device : deviceRepository.findByUserId(user.getId())) {
      switch (apnsClient.send(device, message)) {
        case DELIVERED -> delivered++;
        case DEVICE_GONE -> deviceRepository.delete(device);
        case FAILED -> {
          // Logged by the client. The device stays: the problem may be temporary.
        }
      }
    }
    return delivered;
  }

  private static String truncate(String value, int max) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
  }
}
