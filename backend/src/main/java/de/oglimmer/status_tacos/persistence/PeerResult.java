/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.persistence;

/** What the other server saw when it checked a DOWN monitor before the DOWN alert. */
public enum PeerResult {
  /** The other server sees it DOWN too: the alert was sent. */
  CONFIRMED,
  /** The other server sees it UP: the problem is on this server's side, no alert. */
  PEER_UP,
  /** The other server did not answer: the alert was sent, as without a peer. */
  NO_ANSWER
}
