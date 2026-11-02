package io.apicurio.registry.storage.impl.gitops;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationTaskIdTest {

    @Test
    void randomGeneratesValidId() {
        final ValidationTaskId id = ValidationTaskId.random();
        assertNotNull(id.value());
        assertTrue(ValidationTaskId.isValid(id.value()));
        assertEquals(12, id.value().length());
    }

    @Test
    void randomGeneratesUniqueIds() {
        final ValidationTaskId id1 = ValidationTaskId.random();
        final ValidationTaskId id2 = ValidationTaskId.random();
        assertNotEquals(id1.value(), id2.value());
    }

    @ParameterizedTest
    @ValueSource(strings = {"abcdef012345", "000000000000", "abcdefabcdef", "123456789012"})
    void validIdsAreAccepted(String value) {
        assertDoesNotThrow(() -> new ValidationTaskId(value));
        assertTrue(ValidationTaskId.isValid(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ABCDEF012345",
            "abcdef01234",
            "abcdef0123456",
            "../etc/passwd",
            "../../etc/pas",
            "abcdef01234g",
            "abcdef 12345",
            "abcdef\t12345",
            "abcdef\n12345",
            "/etc/passwd/",
            "..%2F..%2Fetc"
    })
    void invalidIdsAreRejected(String value) {
        assertThrows(IllegalArgumentException.class, () -> new ValidationTaskId(value));
        assertFalse(ValidationTaskId.isValid(value));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void nullAndEmptyAreRejected(String value) {
        assertFalse(ValidationTaskId.isValid(value));
    }

    @Test
    void constructorRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> new ValidationTaskId(null));
    }

    @Test
    void toStringReturnsValue() {
        final ValidationTaskId id = new ValidationTaskId("abcdef012345");
        assertEquals("abcdef012345", id.toString());
    }
}
