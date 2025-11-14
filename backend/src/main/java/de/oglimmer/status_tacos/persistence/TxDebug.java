/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUnitUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@AllArgsConstructor
@Component
@Slf4j
public class TxDebug {

  private final EntityManager entityManager;

  public boolean isEntityDirty(Object entity) {
    Session session = entityManager.unwrap(Session.class);
    return session.isDirty();
  }

  public void printTransactionInfo(String label) {
    boolean active = TransactionSynchronizationManager.isActualTransactionActive();
    boolean readOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
    Integer isolationLevel =
        TransactionSynchronizationManager.getCurrentTransactionIsolationLevel();
    String name = TransactionSynchronizationManager.getCurrentTransactionName();

    log.debug("----- Transaction Info ({}) -----", label);
    log.debug("Active: {}", active);
    log.debug("Read-only: {}", readOnly);
    log.debug("Isolation Level: {}", isolationLevel != null ? isolationLevel : "DEFAULT");
    log.debug("Name: {}", name);
    log.debug("-------------------------------------------");
  }

  public void printPersistenceContextState(String label) {
    try {
      log.debug("----- Persistence Context Info (" + label + ") -----");

      // Get basic session info
      Session session = entityManager.unwrap(Session.class);
      log.debug("Session Status:");
      log.debug("  Open: " + session.isOpen());
      log.debug("  Connected: " + session.isConnected());
      log.debug("  Transaction in progress: " + session.isJoinedToTransaction());
      log.debug("  Dirty: " + session.isDirty());

      // Try to print statistics from SessionFactory
      org.hibernate.SessionFactory factory =
          entityManager.getEntityManagerFactory().unwrap(org.hibernate.SessionFactory.class);
      org.hibernate.stat.Statistics stats = factory.getStatistics();
      boolean wasEnabled = stats.isStatisticsEnabled();

      if (!wasEnabled) {
        stats.setStatisticsEnabled(true);
      }

      log.debug("Statistics:");
      log.debug("  Entity load count: " + stats.getEntityLoadCount());
      log.debug("  Entity fetch count: " + stats.getEntityFetchCount());
      log.debug("  Query execution count: " + stats.getQueryExecutionCount());
      log.debug("  Transaction count: " + stats.getTransactionCount());
      log.debug("  Open session count: " + stats.getSessionOpenCount());

      // Restore original statistics setting
      if (!wasEnabled) {
        stats.setStatisticsEnabled(false);
      }

      // Use HQL to count entities in this session (first-level cache)
      // This is not a direct way to check the persistence context but provides useful information
      log.debug("Entity Manager Info:");
      log.debug(
          "  Contains transaction: "
              + TransactionSynchronizationManager.isActualTransactionActive());
      log.debug("  EntityManager is open: " + entityManager.isOpen());

      // Check if specific entities are managed (you would need to pass them as parameters)
      // entityManager.contains(entity)

    } catch (Exception e) {
      log.debug("Error getting persistence context information: " + e.getMessage());
      e.printStackTrace();
    }
    log.debug("-------------------------------------------");
  }

  public void checkIfManaged(Object entity) {
    PersistenceUnitUtil util = entityManager.getEntityManagerFactory().getPersistenceUnitUtil();
    Object id = util.getIdentifier(entity);

    log.debug("Entity: {}", entity.getClass().getSimpleName());
    log.debug("  ID: {}", id);
    log.debug("  Is managed: {}", entityManager.contains(entity));
  }
}
