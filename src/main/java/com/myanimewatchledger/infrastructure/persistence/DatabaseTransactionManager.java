package com.myanimewatchledger.infrastructure.persistence;

import com.myanimewatchledger.application.port.TransactionRunner;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Executes a unit of work on one JDBC connection. Repository calls sharing this
 * manager reuse that connection while a transaction is active. Nested calls join
 * the existing transaction; only the outermost call commits or rolls back.
 */
public final class DatabaseTransactionManager implements TransactionRunner {
    @FunctionalInterface
    interface SqlWork<T> {
        T execute(Connection connection) throws SQLException;
    }

    private static final class TransactionContext {
        private final Connection connection;
        private boolean rollbackOnly;

        private TransactionContext(Connection connection) {
            this.connection = connection;
        }
    }

    private final DatabaseManager databaseManager;
    private final ThreadLocal<TransactionContext> current = new ThreadLocal<>();

    public DatabaseTransactionManager(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager);
    }

    @Override
    public <T> T executeInTransaction(TransactionRunner.Work<T> work) {
        Objects.requireNonNull(work);
        TransactionContext joined = current.get();
        if (joined != null) {
            try {
                return work.execute();
            } catch (RuntimeException | Error failure) {
                joined.rollbackOnly = true;
                throw failure;
            }
        }

        try (Connection connection = databaseManager.getConnection()) {
            connection.setAutoCommit(false);
            TransactionContext context = new TransactionContext(connection);
            current.set(context);
            try {
                T result = work.execute();
                if (context.rollbackOnly) {
                    throw new DatabaseException(
                            "Transaction was marked rollback-only by a failed repository operation.");
                }
                connection.commit();
                return result;
            } catch (RuntimeException | Error failure) {
                rollback(connection, failure);
                throw failure;
            } catch (SQLException failure) {
                rollback(connection, failure);
                throw new DatabaseException("Unable to commit database transaction.", failure);
            } finally {
                current.remove();
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to start database transaction.", exception);
        }
    }

    <T> T withConnection(SqlWork<T> work) {
        TransactionContext context = current.get();
        if (context != null) {
            try {
                return work.execute(context.connection);
            } catch (SQLException exception) {
                context.rollbackOnly = true;
                throw new DatabaseException("Database operation failed inside a transaction.", exception);
            }
        }
        try (Connection connection = databaseManager.getConnection()) {
            return work.execute(connection);
        } catch (SQLException exception) {
            throw new DatabaseException("Database operation failed.", exception);
        }
    }

    private static void rollback(Connection connection, Throwable original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }
}
