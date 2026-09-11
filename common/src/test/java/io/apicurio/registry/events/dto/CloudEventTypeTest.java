package io.apicurio.registry.events.dto;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CloudEventTypeTest {

    @Test
    void testTypeStrings_matchSpecCompliantValues() {
        assertEquals("io.apicurio.registry.artifact.created", CloudEventType.ARTIFACT_CREATED.type());
        assertEquals("io.apicurio.registry.artifact.updated", CloudEventType.ARTIFACT_UPDATED.type());
        assertEquals("io.apicurio.registry.artifact.deprecated", CloudEventType.ARTIFACT_DEPRECATED.type());
        assertEquals("io.apicurio.registry.artifact.deleted", CloudEventType.ARTIFACT_DELETED.type());
        assertEquals("io.apicurio.registry.artifact.version.published",
                CloudEventType.ARTIFACT_VERSION_PUBLISHED.type());
        assertEquals("io.apicurio.registry.artifact.version.state-changed",
                CloudEventType.ARTIFACT_VERSION_STATE_CHANGED.type());
        assertEquals("io.apicurio.registry.rule.violated", CloudEventType.RULE_VIOLATED.type());
    }

    @Test
    void testTypeStrings_areUnique() {
        Set<String> typeStrings = Arrays.stream(CloudEventType.values()).map(CloudEventType::type)
                .collect(Collectors.toCollection(HashSet::new));
        assertEquals(CloudEventType.values().length, typeStrings.size(), "duplicate CloudEvent type strings found");
    }

    @Test
    void testTypeStrings_followReverseDnsConvention() {
        for (CloudEventType type : CloudEventType.values()) {
            assertTrue(type.type().startsWith("io.apicurio.registry."),
                    () -> type.name() + " does not follow the reverse-DNS naming convention");
        }
    }
}
