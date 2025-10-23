package io.apicurio.registry.services;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for LogLevelValidator.
 */
public class LogLevelValidatorTest {

    private final LogLevelValidator validator = new LogLevelValidator();

    @Test
    public void testValidLogLevels() {
        // Test standard java.util.logging levels
        Assertions.assertTrue(validator.isValidLogLevel("OFF"));
        Assertions.assertTrue(validator.isValidLogLevel("SEVERE"));
        Assertions.assertTrue(validator.isValidLogLevel("WARNING"));
        Assertions.assertTrue(validator.isValidLogLevel("INFO"));
        Assertions.assertTrue(validator.isValidLogLevel("CONFIG"));
        Assertions.assertTrue(validator.isValidLogLevel("FINE"));
        Assertions.assertTrue(validator.isValidLogLevel("FINER"));
        Assertions.assertTrue(validator.isValidLogLevel("FINEST"));
        Assertions.assertTrue(validator.isValidLogLevel("ALL"));

        // Test Quarkus/JBoss Logging aliases
        Assertions.assertTrue(validator.isValidLogLevel("TRACE"));
        Assertions.assertTrue(validator.isValidLogLevel("DEBUG"));
        Assertions.assertTrue(validator.isValidLogLevel("WARN"));
        Assertions.assertTrue(validator.isValidLogLevel("ERROR"));
        Assertions.assertTrue(validator.isValidLogLevel("FATAL"));
    }

    @Test
    public void testInvalidLogLevels() {
        Assertions.assertFalse(validator.isValidLogLevel("INVALID"));
        Assertions.assertFalse(validator.isValidLogLevel("NOTAREALEVEL"));
        Assertions.assertFalse(validator.isValidLogLevel("123"));
        Assertions.assertFalse(validator.isValidLogLevel(""));
        Assertions.assertFalse(validator.isValidLogLevel(null));
        Assertions.assertFalse(validator.isValidLogLevel("   "));

        // Test case sensitivity
        Assertions.assertFalse(validator.isValidLogLevel("debug"));
        Assertions.assertFalse(validator.isValidLogLevel("Info"));
        Assertions.assertFalse(validator.isValidLogLevel("WaRn"));
    }
}
