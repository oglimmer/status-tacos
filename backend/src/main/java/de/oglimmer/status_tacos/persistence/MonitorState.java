/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.persistence;

public enum MonitorState {
  ACTIVE, // Monitor is active and alerts are sent on failures
  SILENT, // Monitor is active but no alerts are sent on failures
  INACTIVE // Monitor is not being monitored
}
