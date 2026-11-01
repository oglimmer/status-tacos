/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.dto;

import de.oglimmer.status_tacos.validation.HttpUrl;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * The full check of a monitor: the other server checks it once with these settings. It needs no
 * monitor of its own.
 */
public record PeerCheckRequestDto(
    @NotBlank @HttpUrl String url,
    Map<String, String> httpHeaders,
    String statusCodeRegex,
    String responseBodyRegex,
    String prometheusKey,
    Double prometheusMinValue,
    Double prometheusMaxValue,
    @Min(1) @Max(10) Integer connectTimeout,
    Boolean useSystemDns) {}
