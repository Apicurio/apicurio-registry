package io.apicurio.registry.agents.rules.compatibility;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rules.compatibility.CompatibilityExecutionResult;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.apicurio.registry.rules.violation.UnprocessableSchemaException;
import io.apicurio.registry.types.ContentTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpToolEvolutionTest {

    private final McpToolCompatibilityChecker checker = new McpToolCompatibilityChecker();

    // Each pair strictly widens the accepted instance set. Inputs may widen in BACKWARD;
    // outputs may widen only in FORWARD. The same pairs pin both reversed directions.
    static Stream<Arguments> wideningSchemas() {
        return Stream.of(
                Arguments.of("required", "{\"type\":\"object\",\"required\":[\"x\"]}", "{\"type\":\"object\"}"),
                Arguments.of("closed optional property", "{\"type\":\"object\",\"additionalProperties\":false}",
                        "{\"type\":\"object\",\"properties\":{\"x\":{\"type\":\"string\"}},\"additionalProperties\":false}"),
                Arguments.of("open property removal", "{\"type\":\"object\",\"properties\":{\"x\":{\"type\":\"string\"}}}",
                        "{\"type\":\"object\"}"),
                Arguments.of("additionalProperties", "{\"type\":\"object\",\"additionalProperties\":false}",
                        "{\"type\":\"object\",\"additionalProperties\":true}"),
                Arguments.of("schema additionalProperties", "{\"type\":\"object\",\"additionalProperties\":{\"type\":\"integer\"}}",
                        "{\"type\":\"object\",\"additionalProperties\":{\"type\":\"number\"}}"),
                Arguments.of("nested type", property("{\"type\":\"integer\"}"), property("{\"type\":\"number\"}")),
                Arguments.of("union type", property("{\"type\":\"string\"}"), property("{\"type\":[\"string\",\"null\"]}")),
                Arguments.of("enum", property("{\"enum\":[\"a\"]}"), property("{\"enum\":[\"a\",\"b\"]}")),
                Arguments.of("minimum", property("{\"type\":\"number\",\"minimum\":5}"), property("{\"type\":\"number\",\"minimum\":0}")),
                Arguments.of("maximum", property("{\"type\":\"number\",\"maximum\":5}"), property("{\"type\":\"number\",\"maximum\":10}")),
                Arguments.of("exclusiveMinimum", property("{\"type\":\"number\",\"exclusiveMinimum\":5}"), property("{\"type\":\"number\",\"exclusiveMinimum\":0}")),
                Arguments.of("exclusiveMaximum", property("{\"type\":\"number\",\"exclusiveMaximum\":5}"), property("{\"type\":\"number\",\"exclusiveMaximum\":10}")),
                Arguments.of("minLength", property("{\"type\":\"string\",\"minLength\":5}"), property("{\"type\":\"string\",\"minLength\":0}")),
                Arguments.of("maxLength", property("{\"type\":\"string\",\"maxLength\":5}"), property("{\"type\":\"string\",\"maxLength\":10}")),
                Arguments.of("items", property("{\"type\":\"array\",\"items\":{\"type\":\"integer\"}}"),
                        property("{\"type\":\"array\",\"items\":{\"type\":\"number\"}}")),
                Arguments.of("minItems", property("{\"type\":\"array\",\"minItems\":2}"), property("{\"type\":\"array\",\"minItems\":1}")),
                Arguments.of("maxItems", property("{\"type\":\"array\",\"maxItems\":2}"), property("{\"type\":\"array\",\"maxItems\":3}")),
                Arguments.of("uniqueItems", property("{\"type\":\"array\",\"uniqueItems\":true}"), property("{\"type\":\"array\",\"uniqueItems\":false}")),
                Arguments.of("minProperties", "{\"type\":\"object\",\"minProperties\":2}", "{\"type\":\"object\",\"minProperties\":1}"),
                Arguments.of("maxProperties", "{\"type\":\"object\",\"maxProperties\":2}", "{\"type\":\"object\",\"maxProperties\":3}"),
                Arguments.of("boolean subschema", property("false"), property("true")),
                Arguments.of("const removal", property("{\"const\":\"a\"}"), property("{}")),
                Arguments.of("multipleOf", property("{\"type\":\"number\",\"multipleOf\":4}"),
                        property("{\"type\":\"number\",\"multipleOf\":2}")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("wideningSchemas")
    void directions(String name, String narrow, String wide) {
        for (String side : List.of("inputSchema", "outputSchema")) {
            boolean input = side.equals("inputSchema");
            assertEquals(input, check(CompatibilityLevel.BACKWARD, tool(side, narrow), tool(side, wide)).isCompatible(), name + " " + side);
            assertEquals(!input, check(CompatibilityLevel.FORWARD, tool(side, narrow), tool(side, wide)).isCompatible(), name + " " + side);
            assertEquals(!input, check(CompatibilityLevel.BACKWARD, tool(side, wide), tool(side, narrow)).isCompatible(), name + " " + side);
            assertEquals(input, check(CompatibilityLevel.FORWARD, tool(side, wide), tool(side, narrow)).isCompatible(), name + " " + side);
            assertFalse(check(CompatibilityLevel.FULL, tool(side, narrow), tool(side, wide)).isCompatible(), name + " " + side);
        }
    }

    @Test
    void nestedFailureHasEscapedPointer() {
        String old = tool("inputSchema", property("{\"type\":\"string\"}"));
        String next = tool("inputSchema", property("{\"type\":\"integer\"}"));
        var result = check(CompatibilityLevel.BACKWARD, old, next);
        assertFalse(result.isCompatible());
        assertTrue(result.getIncompatibleDifferences().stream().anyMatch(d ->
                d.asRuleViolation().getContext().equals("/inputSchema/properties/x~1y~0z/type")));
    }

    @Test
    void outputPresencePreservesGuarantees() {
        String absent = "{\"name\":\"tool\",\"inputSchema\":{\"type\":\"object\"}}";
        String present = tool("outputSchema", "{\"type\":\"object\"}");
        assertTrue(check(CompatibilityLevel.BACKWARD, absent, present).isCompatible());
        assertFalse(check(CompatibilityLevel.BACKWARD, present, absent).isCompatible());
        assertFalse(check(CompatibilityLevel.FORWARD, absent, present).isCompatible());
        assertTrue(check(CompatibilityLevel.FORWARD, present, absent).isCompatible());
        assertFalse(check(CompatibilityLevel.FULL, absent, present).isCompatible());
    }

    @Test
    void transitiveChecksEarlierInputAndOutputContracts() {
        String abc = property("{\"enum\":[\"a\",\"b\",\"c\"]}");
        String ab = property("{\"enum\":[\"a\",\"b\"]}");
        String abd = property("{\"enum\":[\"a\",\"b\",\"d\"]}");
        String a = property("{\"enum\":[\"a\"]}");
        assertTransitive("inputSchema", CompatibilityLevel.BACKWARD, CompatibilityLevel.BACKWARD_TRANSITIVE, abc, ab, abd);
        assertTransitive("outputSchema", CompatibilityLevel.FORWARD, CompatibilityLevel.FORWARD_TRANSITIVE, abc, ab, abd);
        assertTransitive("inputSchema", CompatibilityLevel.FORWARD, CompatibilityLevel.FORWARD_TRANSITIVE, a, abc, ab);
        assertTransitive("outputSchema", CompatibilityLevel.BACKWARD, CompatibilityLevel.BACKWARD_TRANSITIVE, a, abc, ab);
        for (String side : List.of("inputSchema", "outputSchema")) {
            var history = List.of(content(tool(side, abc)), content(tool(side, ab)));
            assertTrue(checker.testCompatibility(CompatibilityLevel.FULL, history, content(tool(side, ab)), Map.of()).isCompatible());
            assertFalse(checker.testCompatibility(CompatibilityLevel.FULL_TRANSITIVE, history, content(tool(side, ab)), Map.of()).isCompatible());
        }
    }

    private void assertTransitive(String side, CompatibilityLevel latest, CompatibilityLevel transitive,
            String first, String second, String next) {
        var history = List.of(content(tool(side, first)), content(tool(side, second)));
        assertTrue(checker.testCompatibility(latest, history, content(tool(side, next)), Map.of()).isCompatible());
        assertFalse(checker.testCompatibility(transitive, history, content(tool(side, next)), Map.of()).isCompatible());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"$ref\":\"#/definitions/x\"}", "{\"oneOf\":[{\"type\":\"string\"}]}",
            "{\"pattern\":\"a.*\"}", "{\"format\":\"email\"}", "{\"unevaluatedProperties\":false}",
            "{\"customAssertion\":true}", "{\"items\":[{\"type\":\"string\"}]}"})
    void unsupportedSchemasAreNotAcceptedAsCompatible(String schema) {
        for (String side : List.of("inputSchema", "outputSchema")) {
            String document = tool(side, property(schema));
            var error = assertThrows(UnprocessableSchemaException.class,
                    () -> check(CompatibilityLevel.BACKWARD, document, document));
            assertTrue(error.getMessage().contains("/" + side), error.getMessage());
            String supported = tool(side, "{\"type\":\"object\"}");
            assertThrows(UnprocessableSchemaException.class,
                    () -> check(CompatibilityLevel.BACKWARD, supported, document));
            assertThrows(UnprocessableSchemaException.class,
                    () -> check(CompatibilityLevel.BACKWARD, document, supported));
            assertTrue(check(CompatibilityLevel.NONE, document, document).isCompatible());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "{}", "{\"inputSchema\":null}",
            "{\"inputSchema\":{\"required\":[1]}}", "{\"inputSchema\":{},\"outputSchema\":null}",
            "{\"inputSchema\":{\"$schema\":\"https://example.invalid/custom\"}}"})
    void unreadableSchemasAreUnprocessable(String document) {
        assertThrows(UnprocessableSchemaException.class,
                () -> check(CompatibilityLevel.BACKWARD, document, document));
    }

    @Test
    void annotationsAndKeywordNamedPropertiesAreNotSchemaAssertions() {
        String schema = """
                {"type":"object","properties":{"oneOf":{"type":"string"}},
                 "enum":[{"oneOf":"a"}],"default":{"$ref":"not-a-reference"}}
                """;
        String next = schema.replace("not-a-reference", "also-not-a-reference");
        assertTrue(check(CompatibilityLevel.FULL, tool("inputSchema", schema), tool("inputSchema", next)).isCompatible());
        assertTrue(check(CompatibilityLevel.FULL, tool("outputSchema", schema), tool("outputSchema", next)).isCompatible());
    }

    @Test
    void simultaneousChangesMustPassBothSides() {
        String old = """
                {"inputSchema":{"type":"object","maxProperties":2},
                 "outputSchema":{"type":"object","maxProperties":2}}
                """;
        String bothWider = old.replace(":2", ":3");
        String bothNarrower = old.replace(":2", ":1");
        assertFalse(check(CompatibilityLevel.BACKWARD, old, bothWider).isCompatible());
        assertFalse(check(CompatibilityLevel.BACKWARD, old, bothNarrower).isCompatible());
        String safe = """
                {"inputSchema":{"type":"object","maxProperties":3},
                 "outputSchema":{"type":"object","maxProperties":1}}
                """;
        assertTrue(check(CompatibilityLevel.BACKWARD, old, safe).isCompatible());
        assertFalse(check(CompatibilityLevel.FORWARD, old, safe).isCompatible());
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"const\":\"a\"", "\"type\":\"number\",\"exclusiveMinimum\":true,\"minimum\":0",
            "\"type\":\"number\",\"exclusiveMaximum\":true,\"maximum\":9"})
    void draft4AssertionsOutsideTheSubsetAreUnprocessable(String assertion) {
        String draft4 = "{\"$schema\":\"http://json-schema.org/draft-04/schema#\",\"type\":\"object\","
                + "\"properties\":{\"x\":{" + assertion + "}}}";
        String withoutOutput = "{\"name\":\"tool\",\"inputSchema\":{\"type\":\"object\"}}";
        // Review case: adding an outputSchema skips the output comparison, so validation alone must reject it.
        String addedOutput = tool("outputSchema", draft4);
        var error = assertThrows(UnprocessableSchemaException.class,
                () -> check(CompatibilityLevel.BACKWARD, withoutOutput, addedOutput));
        assertTrue(error.getMessage().contains("/outputSchema/properties/x/"), error.getMessage());
        String changedInput = tool("inputSchema", draft4);
        assertThrows(UnprocessableSchemaException.class,
                () -> check(CompatibilityLevel.BACKWARD, withoutOutput, changedInput));
        // The same keywords remain supported in later drafts.
        String draft7 = draft4.replace("draft-04", "draft-07").replace("\"exclusiveMinimum\":true", "\"exclusiveMinimum\":0")
                .replace("\"exclusiveMaximum\":true", "\"exclusiveMaximum\":9");
        assertTrue(check(CompatibilityLevel.BACKWARD, withoutOutput, tool("outputSchema", draft7)).isCompatible());
    }

    // 9007199254740993 is not representable as a double; it rounds to ...992. The shared checker
    // compares numbers as doubles, so a change at that precision must be reported as 422, never
    // accepted as two identical schemas.
    @ParameterizedTest
    @ValueSource(strings = {"\"enum\":[%s]", "\"type\":\"number\",\"minimum\":%s",
            "\"type\":\"number\",\"maximum\":%s", "\"type\":\"number\",\"exclusiveMinimum\":%s",
            "\"type\":\"number\",\"exclusiveMaximum\":%s", "\"const\":%s"})
    void numbersBeyondDoublePrecisionAreCompared(String keyword) {
        String low = property("{" + keyword.formatted("9007199254740992.0") + "}");
        String high = property("{" + keyword.formatted("9007199254740993.0") + "}");
        for (String side : List.of("inputSchema", "outputSchema")) {
            String oldTool = tool(side, low);
            String newTool = tool(side, high);
            var error = assertThrows(UnprocessableSchemaException.class,
                    () -> check(CompatibilityLevel.FULL, oldTool, newTool), keyword + " " + side);
            assertTrue(error.getMessage().contains("/" + side + "/properties/x~1y~0z/"), error.getMessage());
            // The exactly representable side alone is accepted.
            assertTrue(check(CompatibilityLevel.FULL, oldTool, oldTool).isCompatible(), keyword + " " + side);
        }
    }

    @Test
    void ordinaryDecimalsAndNestedEnumNumbersAreCompared() {
        String low = property("{\"type\":\"number\",\"maximum\":0.3,\"enum\":[0.1,{\"a\":[0.2]}]}");
        String high = low.replace("0.3", "0.4");
        assertTrue(check(CompatibilityLevel.BACKWARD, tool("inputSchema", low), tool("inputSchema", high)).isCompatible());
        assertFalse(check(CompatibilityLevel.BACKWARD, tool("inputSchema", high), tool("inputSchema", low)).isCompatible());
        String nested = property("{\"enum\":[{\"a\":[9007199254740993]}]}");
        String nestedTool = tool("outputSchema", nested);
        var error = assertThrows(UnprocessableSchemaException.class,
                () -> check(CompatibilityLevel.BACKWARD, nestedTool, nestedTool));
        assertTrue(error.getMessage().endsWith("/enum/0/a/0"), error.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"items", "additionalProperties"})
    void unsupportedAssertionsInsideOtherSchemaPositions(String keyword) {
        String schema = property("{\"" + keyword + "\":{\"oneOf\":[{\"type\":\"string\"}]}}");
        for (String side : List.of("inputSchema", "outputSchema")) {
            String document = tool(side, schema);
            var error = assertThrows(UnprocessableSchemaException.class,
                    () -> check(CompatibilityLevel.FULL, document, document));
            assertTrue(error.getMessage().endsWith("/" + keyword + "/oneOf"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://json-schema.org/draft-04/schema#", "http://json-schema.org/draft-06/schema#",
            "http://json-schema.org/draft-07/schema#", "https://json-schema.org/draft/2019-09/schema",
            "https://json-schema.org/draft/2020-12/schema"})
    void declaredDraftsUseTheSameStructuralDirections(String draft) {
        String narrow = "{\"$schema\":\"" + draft + "\",\"type\":\"object\",\"maxProperties\":1}";
        String wide = narrow.replace(":1", ":2");
        assertTrue(check(CompatibilityLevel.BACKWARD, tool("inputSchema", narrow), tool("inputSchema", wide)).isCompatible());
        assertFalse(check(CompatibilityLevel.BACKWARD, tool("outputSchema", narrow), tool("outputSchema", wide)).isCompatible());
    }

    private CompatibilityExecutionResult check(CompatibilityLevel level, String old, String next) {
        return checker.testCompatibility(level, List.of(content(old)), content(next), Map.of());
    }

    private static String property(String schema) {
        return "{\"type\":\"object\",\"properties\":{\"x/y~z\":" + schema + "}}";
    }

    private static String tool(String side, String schema) {
        return side.equals("inputSchema") ? "{\"name\":\"tool\",\"inputSchema\":" + schema + "}"
                : "{\"name\":\"tool\",\"inputSchema\":{\"type\":\"object\"},\"outputSchema\":" + schema + "}";
    }

    private static TypedContent content(String json) {
        return TypedContent.create(ContentHandle.create(json), ContentTypes.APPLICATION_JSON);
    }
}
