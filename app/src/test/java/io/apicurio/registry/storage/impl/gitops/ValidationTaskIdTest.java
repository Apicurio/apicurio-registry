package io.apicurio.registry.storage.impl.gitops;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;

public class ValidationTaskIdTest {

    @Test
    public void testRandomGeneratesValidCanonicalIds() {
        var generatedIds = new HashSet<String>();
        for (int i = 0; i < 100; i++) {
            ValidationTaskId taskId = ValidationTaskId.random();
            Assertions.assertNotNull(taskId.value());
            Assertions.assertEquals(12, taskId.value().length());
            Assertions.assertTrue(taskId.isValid());
            Assertions.assertTrue(ValidationTaskId.isValid(taskId.value()));
            Assertions.assertEquals(taskId.value(), taskId.toString());

            var reconstructed = new ValidationTaskId(taskId.value());
            Assertions.assertEquals(taskId, reconstructed);
            generatedIds.add(taskId.value());
        }
        Assertions.assertEquals(100, generatedIds.size(), "Generated IDs should be unique");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0123456789ab",
            "abcdef012345",
            "000000000000",
            "ffffffffffff",
            "1a2b3c4d5e6f",
            "a0b1c2d3e4f5"
    })
    public void testValidCanonicalTaskIds(String validId) {
        Assertions.assertTrue(ValidationTaskId.isValid(validId));
        ValidationTaskId taskId = new ValidationTaskId(validId);
        Assertions.assertEquals(validId, taskId.value());
        Assertions.assertTrue(taskId.isValid());
        Assertions.assertEquals(validId, taskId.toString());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "   ",
            "123",
            "12345678901",
            "1234567890123",
            "0123456789ag",
            "0123456789az",
            "0123456789AB",
            "ABCDEF012345",
            "../123456789",
            "123456/../../",
            "123456\\12345",
            "../../etc/passwd",
            "0123456789-a",
            "012345 6789a",
            "0123456789ab.json",
            "0123456789ab\n"
    })
    public void testInvalidTaskIdsRejected(String invalidId) {
        Assertions.assertFalse(ValidationTaskId.isValid(invalidId));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new ValidationTaskId(invalidId));
    }
}
