package io.apicurio.registry.events.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WebhookEventTypesTest {

    @Test
    void testSupported_isExactlyTheFiveLifecycleTypes() {
        assertEquals(EnumSet.of(
                CloudEventType.ARTIFACT_CREATED,
                CloudEventType.ARTIFACT_UPDATED,
                CloudEventType.ARTIFACT_DELETED,
                CloudEventType.ARTIFACT_VERSION_PUBLISHED,
                CloudEventType.ARTIFACT_VERSION_STATE_CHANGED), WebhookEventTypes.SUPPORTED);
    }

    @Test
    void testReserved_isDeprecatedAndRuleViolated() {
        assertEquals(EnumSet.of(CloudEventType.ARTIFACT_DEPRECATED, CloudEventType.RULE_VIOLATED),
                WebhookEventTypes.RESERVED);
    }

    @Test
    void testSupportedAndReserved_partitionCloudEventType() {
        Set<CloudEventType> union = EnumSet.copyOf(WebhookEventTypes.SUPPORTED);
        union.addAll(WebhookEventTypes.RESERVED);
        assertEquals(EnumSet.allOf(CloudEventType.class), union);
        assertTrue(WebhookEventTypes.SUPPORTED.stream().noneMatch(WebhookEventTypes.RESERVED::contains));
    }

    @Test
    void testSupportedValues_matchWireStringsInOrder() {
        assertEquals(List.of(
                "io.apicurio.registry.artifact.created",
                "io.apicurio.registry.artifact.metadata.updated",
                "io.apicurio.registry.artifact.deleted",
                "io.apicurio.registry.artifact.version.created",
                "io.apicurio.registry.artifact.version.state.changed"), WebhookEventTypes.SUPPORTED_VALUES);
    }

    @Test
    void testConstants_areUnmodifiable() {
        assertThrows(UnsupportedOperationException.class,
                () -> WebhookEventTypes.SUPPORTED.add(CloudEventType.RULE_VIOLATED));
        assertThrows(UnsupportedOperationException.class,
                () -> WebhookEventTypes.RESERVED.remove(CloudEventType.RULE_VIOLATED));
        assertThrows(UnsupportedOperationException.class,
                () -> WebhookEventTypes.SUPPORTED_VALUES.add("io.apicurio.registry.rule.violated"));
    }

    @Test
    void testFromSupportedValue_returnsMatchingType() {
        for (CloudEventType type : WebhookEventTypes.SUPPORTED) {
            assertEquals(Optional.of(type), WebhookEventTypes.fromSupportedValue(type.type()));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // reserved
            "io.apicurio.registry.artifact.deprecated", "io.apicurio.registry.rule.violated",
            // not exact: case, whitespace, enum constant name
            "IO.APICURIO.REGISTRY.ARTIFACT.CREATED", " io.apicurio.registry.artifact.created",
            "io.apicurio.registry.artifact.created ", "ARTIFACT_CREATED",
            // unknown
            "", "io.apicurio.registry.artifact", "io.apicurio.registry.group.created"
    })
    void testFromSupportedValue_rejectsReservedAndInexactValues(String value) {
        assertEquals(Optional.empty(), WebhookEventTypes.fromSupportedValue(value));
    }

    @Test
    void testFromSupportedValue_rejectsNull() {
        assertEquals(Optional.empty(), WebhookEventTypes.fromSupportedValue(null));
    }

    @Test
    void testIsReserved_onlyForExactReservedValues() {
        assertTrue(WebhookEventTypes.isReserved("io.apicurio.registry.artifact.deprecated"));
        assertTrue(WebhookEventTypes.isReserved("io.apicurio.registry.rule.violated"));
        assertFalse(WebhookEventTypes.isReserved("io.apicurio.registry.artifact.created"));
        assertFalse(WebhookEventTypes.isReserved("IO.APICURIO.REGISTRY.RULE.VIOLATED"));
        assertFalse(WebhookEventTypes.isReserved("unknown"));
        assertFalse(WebhookEventTypes.isReserved(null));
    }

    @Test
    void testSupportedValues_matchTheOpenApiEnum() throws Exception {
        JsonNode spec;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("META-INF/openapi.json")) {
            spec = new ObjectMapper().readTree(in);
        }
        JsonNode enumNode = spec.path("components").path("schemas").path("WebhookEventType").path("enum");
        List<String> openApiValues = new ArrayList<>();
        enumNode.forEach(value -> openApiValues.add(value.asText()));

        assertEquals(WebhookEventTypes.SUPPORTED_VALUES, openApiValues);
    }
}
