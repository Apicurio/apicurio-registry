package io.apicurio.registry.agents.mcptools.compatibility;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import io.apitomy.datamodels.jsonschema.compat.DiffType;
import io.apitomy.datamodels.jsonschema.compat.Difference;
import io.apitomy.datamodels.jsonschema.ref.JsonPointer;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DifferenceAttributorTest {

    private static final ObjectMapper MAPPER = new JsonMapper();

    private static final String SCHEMA = "{'type':'object','properties':{'a':{'type':'integer'},"
            + "'b':{'type':'integer'}}}";

    @Test
    void testDifferenceWithoutMcpWordingKeepsTheEngineDescription() {
        Difference difference = new Difference(DiffType.NUMBER_TYPE_MINIMUM_ADDED,
                JsonPointer.parse("/properties/a/minimum"), JsonPointer.parse("/properties/a/minimum"));

        assertFalse(difference.getShortDescription().isBlank());
        assertEquals(Optional.of(List.of(new CompatibilityReason(ReasonCode.VALUE_NOT_ACCEPTED,
                "/outputSchema/properties/a", "/inputSchema/properties/a", difference.getShortDescription()))),
                attributor().attribute(difference));
    }

    @Test
    void testDifferenceWhosePointersLeadToDifferentNodesIsNotAttributed() {
        Difference difference = new Difference(DiffType.SUBSCHEMA_TYPE_CHANGED,
                JsonPointer.parse("/properties/a/type"), JsonPointer.parse("/properties/b/type"));

        assertEquals(Optional.empty(), attributor().attribute(difference));
    }

    private static DifferenceAttributor attributor() {
        return new DifferenceAttributor(
                SchemaProjector.project(json(SCHEMA), "/outputSchema", SchemaSide.PRODUCER),
                SchemaProjector.project(json(SCHEMA), "/inputSchema", SchemaSide.CONSUMER), null,
                (emitted, accepted) -> Optional.of(true));
    }

    private static JsonNode json(String singleQuoted) {
        try {
            return MAPPER.readTree(singleQuoted.replace('\'', '"'));
        } catch (JacksonException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
