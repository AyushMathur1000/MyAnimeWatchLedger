package com.myanimewatchledger.infrastructure.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Executes a unit of work on one JDBC connection. All JDBC repositories sharing
 * this manager reuse that connection while a transaction is active.
 */
public final class DatabaseTransactionManager {
 @FunctionalInterface public interface TransactionWork<T> { T execute(); }
 @FunctionalInterface interface SqlWork<T> { T execute(Connection connection) throws SQLException; }

 private final DatabaseManager databaseManager;
 private final ThreadLocal<Connection> currentConnection = new ThreadLocal<>();

 public DatabaseTransactionManager(DatabaseManager databaseManager) {
  this.databaseManager = Objects.requireNonNull(databaseManager);
 }

 public <T> T executeInTransaction(TransactionWork<T> work) {
  Objects.requireNonNull(work);
  if (currentConnection.get() != null) throw new IllegalStateException("Nested transactions are not supported.");
  try (Connection connection = databaseManager.getConnection()) {
   connection.setAutoCommit(false);
   currentConnection.set(connection);
   try {
    T result = work.execute();
    connection.commit();
    return result;
   } catch (RuntimeException | Error failure) {
    try { connection.rollback(); } catch (SQLException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
    throw failure;
   } finally {
    currentConnection.remove();
    if (!connection.isClosed()) connection.setAutoCommit(true);
   }
  } catch (SQLException exception) {
   throw new DatabaseException("Database transaction failed.", exception);
  }
 }

 <T> T withConnection(SqlWork<T> work) {
  Connection active = currentConnection.get();
  if (active != null) {
   try { return work.execute(active); }
   catch (SQLException exception) { throw new DatabaseException("Database operation failed inside a transaction.", exception); }
  }
  try (Connection connection = databaseManager.getConnection()) { return work.execute(connection); }
  catch (SQLException exception) { throw new DatabaseException("Database operation failed.", exception); }
 }
}
