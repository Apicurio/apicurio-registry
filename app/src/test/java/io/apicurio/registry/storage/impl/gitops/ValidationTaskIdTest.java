package io.apicurio.registry.storage.impl.gitops;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationTaskIdTest {

    @Test
    void randomGeneratesValidId() {
        final var taskId = ValidationTaskId.random();
        assertNotNull(taskId);
        assertTrue(ValidationTaskId.isValid(taskId.value()));
        assertEquals(12, taskId.value().length());
    }

    @ParameterizedTest
    @ValueSource(strings = {"aabbccddeeff", "000000000000", "abcdef123456"})
    void acceptsValidHexIds(String value) {
        assertTrue(ValidationTaskId.isValid(value));
        final var taskId = new ValidationTaskId(value);
        assertEquals(value, taskId.value());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "../../../etc",
            "/etc/passwd",
            "AABBCCDDEEFF",
            "abc",
            "aabbccddeeffgg",
            "not-hex-value",
            "",
            "aabbccddeef/"
    })
    void rejectsInvalidIds(String value) {
        assertFalse(ValidationTaskId.isValid(value));
        assertThrows(IllegalArgumentException.class, () -> new ValidationTaskId(value));
    }

    @Test
    void rejectsNull() {
        assertFalse(ValidationTaskId.isValid(null));
        assertThrows(IllegalArgumentException.class, () -> new ValidationTaskId(null));
    }
}
