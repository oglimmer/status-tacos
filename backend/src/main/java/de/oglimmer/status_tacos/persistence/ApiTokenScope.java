/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.persistence;

/** What an API token can do. */
public enum ApiTokenScope {
  /** Read the check results with Prometheus queries (/prom). */
  GRAFANA,
  /**
   * The other Status Tacos server calls /peer of this server. Only a member of the peer tenant can
   * create one, and it stops working when the user leaves that tenant.
   */
  PEER
}
