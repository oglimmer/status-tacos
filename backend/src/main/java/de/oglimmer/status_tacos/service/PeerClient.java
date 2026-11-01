/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import de.oglimmer.status_tacos.config.PeerConfig;
import de.oglimmer.status_tacos.dto.PeerCheckRequestDto;
import de.oglimmer.status_tacos.dto.PeerCheckResponseDto;
import de.oglimmer.status_tacos.dto.PeerDistressDto;
import java.time.Duration;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/** Calls /peer of the other server. */
@Component
public class PeerClient {

  private final RestTemplate restTemplate;

  public PeerClient(PeerConfig peerConfig) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofSeconds(5));
    // The other server needs up to its total check timeout (10 s) for a check
    requestFactory.setReadTimeout(peerConfig.getConfirmTimeout());
    this.restTemplate = new RestTemplate(requestFactory);
  }

  /**
   * @param baseUrl for example https://b.example.com/api/peer
   * @param authorization the Authorization header of the peer monitor
   */
  public PeerCheckResponseDto check(
      String baseUrl, String authorization, PeerCheckRequestDto request) {
    PeerCheckResponseDto response =
        restTemplate.postForObject(
            baseUrl + "/check", entity(authorization, request), PeerCheckResponseDto.class);
    if (response == null) {
      throw new IllegalStateException("Empty answer from " + baseUrl);
    }
    return response;
  }

  public void distress(String baseUrl, String authorization, PeerDistressDto distress) {
    restTemplate.postForEntity(baseUrl + "/distress", entity(authorization, distress), Void.class);
  }

  private static <T> HttpEntity<T> entity(String authorization, T body) {
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.AUTHORIZATION, authorization);
    headers.setContentType(MediaType.APPLICATION_JSON);
    return new HttpEntity<>(body, headers);
  }
}
