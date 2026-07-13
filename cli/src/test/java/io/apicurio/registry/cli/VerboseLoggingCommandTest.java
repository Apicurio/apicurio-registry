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
        var httpLogger = Logger.getLogger(HTTP_LOGGER);
        var previousLevel = httpLogger.getLevel();
        var records = new CopyOnWriteArrayList<String>();
        var handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        handler.setLevel(Level.ALL);
        httpLogger.setLevel(Level.FINE);
        httpLogger.addHandler(handler);
        try {
            executeAndAssertSuccess("group", "--verbose", "--output-type", "json");
        } finally {
            httpLogger.removeHandler(handler);
            httpLogger.setLevel(previousLevel);
        }

        assertThat(records)
                .as("Verbose mode should log the outgoing request line")
                .anySatisfy(msg -> assertThat(msg).contains("--> GET").contains("/apis/registry/v3"));
        assertThat(records)
                .as("Verbose mode should log the response status line")
                .anySatisfy(msg -> assertThat(msg).contains("<-- 200"));
    }

    @Test
    public void testWithoutVerboseNoHttpLogs() {
        var httpLogger = Logger.getLogger(HTTP_LOGGER);
        var previousLevel = httpLogger.getLevel();
        List<String> records = new CopyOnWriteArrayList<>();
        var handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        handler.setLevel(Level.ALL);
        httpLogger.setLevel(Level.FINE);
        httpLogger.addHandler(handler);
        try {
            executeAndAssertSuccess("group", "--output-type", "json");
        } finally {
            httpLogger.removeHandler(handler);
            httpLogger.setLevel(previousLevel);
        }

        assertThat(records)
                .as("Without --verbose no HTTP wire logging should be produced")
                .isEmpty();
    }
}
