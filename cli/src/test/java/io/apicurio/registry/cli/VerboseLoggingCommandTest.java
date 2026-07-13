package io.apicurio.registry.cli;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@code --verbose} logs raw HTTP request and response details.
 */
@QuarkusTest
public class VerboseLoggingCommandTest extends AbstractCLITest {

    private static final String HTTP_LOGGER = "io.apicurio.registry.client.http";

    @Test
    public void testVerboseLogsRequestAndResponse() {
        var records = captureHttpLogs("group", "--verbose", "--output-type", "json");

        assertThat(records)
                .as("Verbose mode should log the outgoing request line")
                .anySatisfy(msg -> assertThat(msg).contains("--> GET").contains("/apis/registry/v3"));
        assertThat(records)
                .as("Verbose mode should log the response status line")
                .anySatisfy(msg -> assertThat(msg).contains("<-- 200"));
    }

    @Test
    public void testWithoutVerboseNoHttpLogs() {
        var records = captureHttpLogs("group", "--output-type", "json");

        assertThat(records)
                .as("Without --verbose no HTTP wire logging should be produced")
                .isEmpty();
    }

    /**
     * Runs the given command while capturing everything logged to the HTTP wire logger at FINE.
     */
    private List<String> captureHttpLogs(String... command) {
        var httpLogger = Logger.getLogger(HTTP_LOGGER);
        var previousLevel = httpLogger.getLevel();
        var handler = new CapturingHandler();
        handler.setLevel(Level.ALL);
        httpLogger.setLevel(Level.FINE);
        httpLogger.addHandler(handler);
        try {
            executeAndAssertSuccess(command);
        } finally {
            httpLogger.removeHandler(handler);
            httpLogger.setLevel(previousLevel);
        }
        return handler.messages;
    }

    /**
     * A log handler that collects logged messages in memory for later assertions.
     */
    private static final class CapturingHandler extends Handler {

        private final List<String> messages = new CopyOnWriteArrayList<>();

        @Override
        public void publish(LogRecord logRecord) {
            messages.add(logRecord.getMessage());
        }

        @Override
        public void flush() {
            // No-op: messages are kept in memory, there is nothing to flush.
        }

        @Override
        public void close() {
            // No-op: messages are kept in memory, there is nothing to close.
        }
    }
}
