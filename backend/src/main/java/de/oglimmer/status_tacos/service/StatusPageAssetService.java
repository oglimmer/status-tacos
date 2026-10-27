/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import static java.util.Map.entry;

import de.oglimmer.status_tacos.dto.StatusPageAssetDto;
import de.oglimmer.status_tacos.persistence.TenantStatusPageAsset;
import de.oglimmer.status_tacos.repository.TenantRepository;
import de.oglimmer.status_tacos.repository.TenantStatusPageAssetRepository;
import de.oglimmer.status_tacos.repository.TenantStatusPageAssetRepository.AssetInfo;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The files of the public status page of a tenant: fonts and images that the server serves itself,
 * so the page needs no third-party host (GDPR). The caller of the editor methods checks the access.
 */
@Service
@RequiredArgsConstructor
public class StatusPageAssetService {

  /** The database needs a max_allowed_packet above this (stage: 16M, the MariaDB default). */
  public static final int MAX_FILE_BYTES = 1024 * 1024;

  public static final int MAX_FILES = 20;
  public static final long MAX_TOTAL_BYTES = 5L * 1024 * 1024;

  /**
   * The allowed file types, by extension. The server sets the content type from this list, never
   * from the upload, so a file cannot pretend to be HTML.
   */
  static final Map<String, String> CONTENT_TYPES =
      Map.ofEntries(
          entry("woff2", "font/woff2"),
          entry("woff", "font/woff"),
          entry("ttf", "font/ttf"),
          entry("otf", "font/otf"),
          entry("png", "image/png"),
          entry("jpg", "image/jpeg"),
          entry("jpeg", "image/jpeg"),
          entry("gif", "image/gif"),
          entry("webp", "image/webp"),
          entry("avif", "image/avif"),
          entry("svg", "image/svg+xml"),
          entry("ico", "image/x-icon"));

  private final TenantRepository tenantRepository;
  private final TenantStatusPageAssetRepository assetRepository;
  private final PublicStatusService publicStatusService;

  @Transactional(readOnly = true)
  public List<StatusPageAssetDto> list(Integer tenantId) {
    return assetRepository.findByTenantIdOrderByName(tenantId).stream()
        .map(StatusPageAssetService::toDto)
        .toList();
  }

  /**
   * Adds the file, or replaces the file with the same name.
   *
   * @param data at most {@link #MAX_FILE_BYTES} + 1 bytes: one more byte means the file is too big
   * @throws IllegalArgumentException with a message for the user
   */
  @Transactional
  public StatusPageAssetDto save(Integer tenantId, String name, byte[] data) {
    String contentType = contentType(name);
    if (data.length == 0) {
      throw new IllegalArgumentException("The file is empty.");
    }
    if (data.length > MAX_FILE_BYTES) {
      throw new IllegalArgumentException(
          "A file must not be bigger than " + MAX_FILE_BYTES / 1024 + " KB.");
    }
    if (assetRepository.countByTenantIdAndNameNot(tenantId, name) >= MAX_FILES) {
      throw new IllegalArgumentException("A status page can have at most " + MAX_FILES + " files.");
    }
    if (assetRepository.sumSizeExcept(tenantId, name) + data.length > MAX_TOTAL_BYTES) {
      throw new IllegalArgumentException(
          "All files together must not be bigger than " + MAX_TOTAL_BYTES / 1024 / 1024 + " MB.");
    }
    TenantStatusPageAsset asset =
        assetRepository
            .findByTenantIdAndName(tenantId, name)
            .orElseGet(() -> TenantStatusPageAsset.builder().tenantId(tenantId).name(name).build());
    asset.setContentType(contentType);
    asset.setSize(data.length);
    asset.setSha256(sha256(data));
    asset.setData(data);
    asset = assetRepository.save(asset);
    publicStatusService.evict(tenantId);
    return StatusPageAssetDto.builder()
        .name(asset.getName())
        .contentType(asset.getContentType())
        .size(asset.getSize())
        .updatedAt(asset.getUpdatedAt())
        .reference(reference(asset.getName()))
        .build();
  }

  /**
   * @return false if there is no such file
   */
  @Transactional
  public boolean delete(Integer tenantId, String name) {
    Optional<TenantStatusPageAsset> asset = assetRepository.findByTenantIdAndName(tenantId, name);
    asset.ifPresent(assetRepository::delete);
    publicStatusService.evict(tenantId);
    return asset.isPresent();
  }

  /**
   * A file for the public page.
   *
   * @return empty if there is no active tenant with the code, or no such file
   */
  @Transactional(readOnly = true)
  public Optional<TenantStatusPageAsset> find(String tenantCode, String name) {
    return tenantRepository
        .findActiveByCode(tenantCode)
        .flatMap(t -> assetRepository.findByTenantIdAndName(t.getId(), name));
  }

  /** The part of the hash in the URL of the file. A request with it can be cached for long. */
  public static String version(TenantStatusPageAsset asset) {
    return PublicStatusService.version(asset.getSha256());
  }

  /**
   * @throws IllegalArgumentException if the name or the file type is not allowed
   */
  static String contentType(String name) {
    if (name == null || !name.matches(StatusPageRenderer.ASSET_NAME)) {
      throw new IllegalArgumentException(
          "The file name can have only letters, digits, \".\", \"_\" and \"-\", and at most 100"
              + " characters.");
    }
    int dot = name.lastIndexOf('.');
    String type =
        dot < 0 ? null : CONTENT_TYPES.get(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    if (type == null) {
      throw new IllegalArgumentException(
          "Allowed files: fonts (woff2, woff, ttf, otf) and images (png, jpg, gif, webp, avif, svg,"
              + " ico).");
    }
    return type;
  }

  private static StatusPageAssetDto toDto(AssetInfo asset) {
    return StatusPageAssetDto.builder()
        .name(asset.getName())
        .contentType(asset.getContentType())
        .size(asset.getSize())
        .updatedAt(asset.getUpdatedAt())
        .reference(reference(asset.getName()))
        .build();
  }

  private static String reference(String name) {
    return "asset:" + name;
  }

  private static String sha256(byte[] data) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
