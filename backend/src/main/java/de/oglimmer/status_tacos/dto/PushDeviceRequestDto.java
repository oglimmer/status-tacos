/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.dto;

import de.oglimmer.status_tacos.persistence.PushDevice;
import lombok.Data;

/** The APNs device token of one install of the iOS app. */
@Data
public class PushDeviceRequestDto {

  /** Hex device token. */
  private String token;

  private PushDevice.Environment environment;

  /** For example "Oliver's iPhone". */
  private String deviceName;
}
