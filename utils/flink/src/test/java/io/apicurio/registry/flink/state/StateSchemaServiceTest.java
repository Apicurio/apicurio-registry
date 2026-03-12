package io.apicurio.registry.flink.state;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StateSchemaServiceTest {

    @Test
    void testDefaultStateGroupSuffix() {
        assertEquals("-state", StateSchemaService.DEFAULT_STATE_GROUP_SUFFIX);
    }

    @Test
    void testStateGroupIdGeneration() {
        final String baseGroup = "my-app";
        final String expectedStateGroup = baseGroup + StateSchemaService.DEFAULT_STATE_GROUP_SUFFIX;
        assertEquals("my-app-state", expectedStateGroup);
    }

    @Test
    void testCustomSuffixStateGroupIdGeneration() {
        final String baseGroup = "my-app";
        final String customSuffix = "-schemas";
        final String expectedStateGroup = baseGroup + customSuffix;
        assertEquals("my-app-schemas", expectedStateGroup);
    }
}
