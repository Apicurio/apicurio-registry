package io.apicurio.registry.storage.impl.sql;

import io.agroal.api.AgroalDataSource;
import org.slf4j.Logger;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;

/**
 * Utility class for database connection retry logic with exponential backoff.
 * Handles transient connection failures such as DNS resolution issues, connection timeouts,
 * and temporary network disruptions.
 */
public final class ConnectionRetryUtil {

    private ConnectionRetryUtil() {
        // Utility class
    }

    /**
     * Attempts to get a database connection with retry logic and exponential backoff.
     *
     * @param dataSource the data source to get a connection from
     * @param config the retry configuration
     * @param log the logger for retry attempt logging
     * @return a database connection
     * @throws SQLException if unable to get a connection after all retry attempts
     */
    public static Connection getConnectionWithRetry(AgroalDataSource dataSource, ConnectionRetryConfig config, Logger log)
            throws SQLException {

        if (!config.isEnabled()) {
            return dataSource.getConnection();
        }

        int attempt = 0;
        SQLException lastException = null;

        while (attempt < config.getMaxAttempts()) {
            try {
                Connection connection = dataSource.getConnection();
                if (attempt > 0) {
                    log.info("Successfully obtained database connection after {} attempt(s)", attempt + 1);
                }
                return connection;
            } catch (SQLException e) {
                lastException = e;

                if (!isTransientException(e)) {
                    log.error("Non-transient database connection error, failing immediately: {}", e.getMessage());
                    throw e;
                }

                attempt++;
                if (attempt < config.getMaxAttempts()) {
                    long delayMs = calculateRetryDelay(attempt, config);
                    log.warn("Transient database connection failure (attempt {}/{}): {}. Retrying in {} ms...",
                            attempt, config.getMaxAttempts(), getRootCauseMessage(e), delayMs);

                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new SQLException("Connection retry interrupted", e);
                    }
                }
            }
        }

        log.error("Failed to obtain database connection after {} attempts", config.getMaxAttempts());
        throw lastException;
    }

    /**
     * Determines if an exception represents a transient error that should be retried.
     *
     * @param e the exception to check
     * @return true if the exception is transient and retryable
     */
    public static boolean isTransientException(SQLException e) {
        // Check root cause for network-related exceptions
        Throwable cause = getRootCause(e);

        // DNS resolution failure
        if (cause instanceof UnknownHostException) {
            return true;
        }

        // TCP connection refused or failed
        if (cause instanceof ConnectException) {
            return true;
        }

        // Connection timeout
        if (cause instanceof SocketTimeoutException) {
            return true;
        }

        // JDBC transient connection exceptions
        if (e instanceof SQLTransientConnectionException) {
            return true;
        }

        // Check SQLState for connection-related errors (08xxx class)
        String sqlState = e.getSQLState();
        if (sqlState != null && sqlState.startsWith("08")) {
            return true;
        }

        // Check for transient exception anywhere in the cause chain
        Throwable current = e;
        while (current != null) {
            if (current instanceof SQLTransientConnectionException) {
                return true;
            }
            current = current.getCause();
        }

        return false;
    }

    /**
     * Calculates the retry delay using exponential backoff.
     *
     * @param attempt the current attempt number (1-based)
     * @param config the retry configuration
     * @return the delay in milliseconds before the next retry
     */
    public static long calculateRetryDelay(int attempt, ConnectionRetryConfig config) {
        double delay = config.getInitialDelayMs() * Math.pow(config.getBackoffMultiplier(), attempt - 1);
        return Math.min((long) delay, config.getMaxDelayMs());
    }

    /**
     * Gets the root cause of an exception chain.
     */
    private static Throwable getRootCause(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }

    /**
     * Gets a descriptive message from the root cause of an exception.
     */
    private static String getRootCauseMessage(Throwable t) {
        Throwable root = getRootCause(t);
        String message = root.getMessage();
        if (message == null || message.isEmpty()) {
            message = root.getClass().getSimpleName();
        }
        return root.getClass().getSimpleName() + ": " + message;
    }
}
