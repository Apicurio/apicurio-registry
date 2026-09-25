package io.apicurio.registry.storage.impl.sql;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ConnectionRetryUtilTest {

    private ConnectionRetryConfig config;

    @BeforeEach
    void setUp() {
        config = mock(ConnectionRetryConfig.class);
        when(config.isEnabled()).thenReturn(true);
        when(config.getMaxAttempts()).thenReturn(10);
        when(config.getInitialDelayMs()).thenReturn(1000L);
        when(config.getMaxDelayMs()).thenReturn(30000L);
        when(config.getBackoffMultiplier()).thenReturn(2.0);
    }

    @Test
    void testIsTransientException_UnknownHostException() {
        SQLException e = new SQLException("Connection failed", new UnknownHostException("db.example.com"));
        Assertions.assertTrue(ConnectionRetryUtil.isTransientException(e));
    }

    @Test
    void testIsTransientException_ConnectException() {
        SQLException e = new SQLException("Connection refused", new ConnectException("Connection refused"));
        Assertions.assertTrue(ConnectionRetryUtil.isTransientException(e));
    }

    @Test
    void testIsTransientException_SocketTimeoutException() {
        SQLException e = new SQLException("Timeout", new SocketTimeoutException("Read timed out"));
        Assertions.assertTrue(ConnectionRetryUtil.isTransientException(e));
    }

    @Test
    void testIsTransientException_SQLTransientConnectionException() {
        SQLTransientConnectionException e = new SQLTransientConnectionException("Connection temporarily unavailable");
        Assertions.assertTrue(ConnectionRetryUtil.isTransientException(e));
    }

    @Test
    void testIsTransientException_ConnectionSQLState08() {
        SQLException e = new SQLException("Connection error", "08001");
        Assertions.assertTrue(ConnectionRetryUtil.isTransientException(e));
    }

    @Test
    void testIsTransientException_AuthenticationError() {
        SQLException e = new SQLException("Authentication failed", "28000");
        Assertions.assertFalse(ConnectionRetryUtil.isTransientException(e));
    }

    @Test
    void testIsTransientException_SyntaxError() {
        SQLException e = new SQLException("Syntax error", "42000");
        Assertions.assertFalse(ConnectionRetryUtil.isTransientException(e));
    }

    @Test
    void testIsTransientException_GenericSQLException() {
        SQLException e = new SQLException("Some error");
        Assertions.assertFalse(ConnectionRetryUtil.isTransientException(e));
    }

    @Test
    void testCalculateRetryDelay_FirstAttempt() {
        long delay = ConnectionRetryUtil.calculateRetryDelay(1, config);
        Assertions.assertEquals(1000L, delay);
    }

    @Test
    void testCalculateRetryDelay_SecondAttempt() {
        long delay = ConnectionRetryUtil.calculateRetryDelay(2, config);
        Assertions.assertEquals(2000L, delay);
    }

    @Test
    void testCalculateRetryDelay_ThirdAttempt() {
        long delay = ConnectionRetryUtil.calculateRetryDelay(3, config);
        Assertions.assertEquals(4000L, delay);
    }

    @Test
    void testCalculateRetryDelay_ExceedsMax() {
        // Attempt 6 would be 1000 * 2^5 = 32000, but max is 30000
        long delay = ConnectionRetryUtil.calculateRetryDelay(6, config);
        Assertions.assertEquals(30000L, delay);
    }

    @Test
    void testCalculateRetryDelay_VerifyExponentialBackoff() {
        // Verify the exponential nature: delay doubles each attempt
        long delay1 = ConnectionRetryUtil.calculateRetryDelay(1, config);
        long delay2 = ConnectionRetryUtil.calculateRetryDelay(2, config);
        long delay3 = ConnectionRetryUtil.calculateRetryDelay(3, config);
        long delay4 = ConnectionRetryUtil.calculateRetryDelay(4, config);

        Assertions.assertEquals(1000L, delay1);
        Assertions.assertEquals(2000L, delay2);
        Assertions.assertEquals(4000L, delay3);
        Assertions.assertEquals(8000L, delay4);
    }
}
