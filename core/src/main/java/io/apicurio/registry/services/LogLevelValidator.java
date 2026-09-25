package io.apicurio.registry.services;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Arrays;
import java.util.List;

/**
 * Validator for log level configuration values.
 */
@ApplicationScoped
public class LogLevelValidator {

    private static final List<String> VALID_LOG_LEVELS = Arrays.asList(
        "OFF",
        "SEVERE",
        "WARNING",
        "INFO",
        "CONFIG",
        "FINE",
        "FINER",
        "FINEST",
        "ALL",
        // Quarkus/JBoss Logging aliases
        "TRACE",
        "DEBUG",
        "WARN",
        "ERROR",
        "FATAL"
    );

    /**
     * Validates if the given log level string is a valid log level.
     *
     * @param logLevel the log level string to validate
     * @return true if valid, false otherwise
     */
    public boolean isValidLogLevel(String logLevel) {
        if (logLevel == null || logLevel.isBlank()) {
            return false;
        }

        // Check if it's in our list of known valid levels
        if (VALID_LOG_LEVELS.contains(logLevel)) {
            return true;
        }

        return false;
    }
}
