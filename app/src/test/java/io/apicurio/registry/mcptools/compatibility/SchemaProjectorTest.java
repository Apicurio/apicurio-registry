package io.apicurio.registry.mcptools.compatibility;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaProjectorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void testKeepsEvaluatedKeywordsAndDropsAnnotations() {
        SchemaProjection projection = project("{'title':'t','type':'object','description':'d',"
                + "'properties':{'a':{'type':'string','default':'x','examples':['x']},'b':true},"
                + "'required':['a'],'additionalProperties':{'type':'integer','$comment':'c'}}");

        assertEquals(json("{'type':'object','properties':{'a':{'type':'string'},'b':true},"
                + "'required':['a'],'additionalProperties':{'type':'integer'}}"), projection.projected());
        assertTrue(projection.limitations().isEmpty());
    }

    @Test
    void testRootKeywordIsLimitationOnRoot() {
        SchemaProjection projection = project("{'type':'object','oneOf':[{'type':'object'}]}");

        assertEquals(json("{'type':'object'}"), projection.projected());
        assertEquals(List.of(new CompatibilityLimitation(LimitationCode.UNSUPPORTED_KEYWORD,
                SchemaSide.CONSUMER, "/inputSchema", "/inputSchema/oneOf", "'oneOf' is not evaluated yet")),
                projection.limitations());
    }

    @Test
    void testPropertyKeywordIsLimitationOnProperty() {
        SchemaProjection projection = project(
                "{'type':'object','properties':{'a':{'type':'string','pattern':'^x'}}}");

        assertEquals(json("{'type':'object','properties':{'a':{'type':'string'}}}"),
                projection.projected());
        assertEquals(List.of(new CompatibilityLimitation(LimitationCode.UNSUPPORTED_KEYWORD,
                SchemaSide.CONSUMER, "/inputSchema/properties/a", "/inputSchema/properties/a/pattern",
                "'pattern' is not evaluated yet")), projection.limitations());
    }

    @Test
    void testNestedStructureIsDepthLimit() {
        SchemaProjection projection = project("{'type':'object','properties':{'tags':{'type':'array',"
                + "'items':{'type':'string'}}},'additionalProperties':{'required':['x']}}");

        assertEquals(json("{'type':'object','properties':{'tags':{'type':'array'}},"
                + "'additionalProperties':{}}"), projection.projected());
        assertEquals(List.of(
                new CompatibilityLimitation(LimitationCode.DEPTH_LIMIT_REACHED, SchemaSide.CONSUMER,
                        "/inputSchema/properties/tags", "/inputSchema/properties/tags/items",
                        "Nested 'items' is not evaluated yet"),
                new CompatibilityLimitation(LimitationCode.DEPTH_LIMIT_REACHED, SchemaSide.CONSUMER,
                        "/inputSchema/additionalProperties", "/inputSchema/additionalProperties/required",
                        "Nested 'required' is not evaluated yet")), projection.limitations());
    }

    @Test
    void testPropertiesNamedLikeKeywordsAreKept() {
        SchemaProjection projection = project("{'type':'object','properties':{'enum':{'type':'string'},"
                + "'format':{'type':'string'},'$ref':{'type':'string'}}}");

        assertEquals(List.of("enum", "format", "$ref"), projection.propertyNames());
        assertTrue(projection.limitations().isEmpty());
    }

    @Test
    void testRootTypeGivenAsArrayIsRemoved() {
        SchemaProjection projection = project("{'type':['object','null'],'properties':{"
                + "'a':{'type':'string'}}}");

        assertEquals(json("{'properties':{'a':{'type':'string'}}}"), projection.projected());
        assertEquals(List.of(new CompatibilityLimitation(LimitationCode.UNSUPPORTED_KEYWORD,
                SchemaSide.CONSUMER, "/inputSchema", "/inputSchema/type",
                "'type' given as an array is not evaluated yet")), projection.limitations());
    }

    @Test
    void testPropertyUnionKeepsTheTypesItAccepts() {
        SchemaProjection projection = project("{'type':'object','properties':{"
                + "'a':{'type':['string','null']},'b':{'type':['string','string','null']},"
                + "'c':{'type':['null','string']}}}");

        assertEquals(json("{'type':'object','properties':{'a':{'type':['string','null']},"
                + "'b':{'type':['string','null']},'c':{'type':['null','string']}}}"),
                projection.projected());
        assertTrue(projection.limitations().isEmpty());
    }

    @Test
    void testPropertyUnionOfOneTypeIsWrittenAsThatType() {
        SchemaProjection projection = project("{'type':'object','properties':{'a':{'type':['string']},"
                + "'b':{'type':['integer','number']},'c':{'type':['number','integer','null']}}}");

        assertEquals(json("{'type':'object','properties':{'a':{'type':'string'},'b':{'type':'number'},"
                + "'c':{'type':['number','null']}}}"), projection.projected());
        assertTrue(projection.limitations().isEmpty());
    }

    @Test
    void testPropertyTypeThatNamesNoTypeIsLimitation() {
        SchemaProjection projection = project("{'type':'object','properties':{'a':{'type':[]},"
                + "'b':{'type':['string',7]}}}");

        assertEquals(json("{'type':'object','properties':{'a':{},'b':{}}}"), projection.projected());
        assertEquals(List.of(
                new CompatibilityLimitation(LimitationCode.UNSUPPORTED_KEYWORD, SchemaSide.CONSUMER,
                        "/inputSchema/properties/a", "/inputSchema/properties/a/type",
                        "'type' does not list type names"),
                new CompatibilityLimitation(LimitationCode.UNSUPPORTED_KEYWORD, SchemaSide.CONSUMER,
                        "/inputSchema/properties/b", "/inputSchema/properties/b/type",
                        "'type' does not list type names")), projection.limitations());
    }

    @Test
    void testProducerFormatIsRemovedWithoutLimitation() {
        SchemaProjection projection = SchemaProjector.project(
                json("{'type':'object','format':'x','properties':{'a':{'type':'string','format':'email'}}}"),
                "/outputSchema", SchemaSide.PRODUCER);

        assertEquals(json("{'type':'object','properties':{'a':{'type':'string'}}}"),
                projection.projected());
        assertTrue(projection.limitations().isEmpty());
    }

    @Test
    void testConsumerFormatStaysLimitation() {
        SchemaProjection projection = project(
                "{'type':'object','properties':{'a':{'type':'string','format':'email'}}}");

        assertEquals(json("{'type':'object','properties':{'a':{'type':'string'}}}"),
                projection.projected());
        assertEquals(List.of(new CompatibilityLimitation(LimitationCode.UNSUPPORTED_KEYWORD,
                SchemaSide.CONSUMER, "/inputSchema/properties/a", "/inputSchema/properties/a/format",
                "'format' is not evaluated yet")), projection.limitations());
    }

    @Test
    void testRootWithDeclaredPropertiesAndNoAdditionalPropertiesIsClosable() {
        SchemaProjection projection = project("{'type':'object','properties':{'a':{'type':'string'}}}");

        assertTrue(projection.closable());
        assertEquals(json("{'type':'object','properties':{'a':{'type':'string'}},"
                + "'additionalProperties':false}"), projection.closed());
    }

    @Test
    void testRootWithoutDeclaredPropertiesIsNotClosable() {
        assertFalse(project("{'type':'object'}").closable());
        assertFalse(project("{'type':'object','properties':{}}").closable());
    }

    @Test
    void testRootWithExplicitAdditionalPropertiesIsNotClosable() {
        assertFalse(project("{'type':'object','properties':{'a':{}},'additionalProperties':true}").closable());
        assertFalse(project("{'type':'object','properties':{'a':{}},"
                + "'additionalProperties':{'type':'string'}}").closable());
    }

    @Test
    void testUnsupportedDialectLeavesNothingToCompare() {
        SchemaProjection projection = project("{'$schema':'https://example.invalid/custom','type':'object'}");

        assertFalse(projection.dialectSupported());
        assertNull(projection.projected());
        assertEquals(List.of("/inputSchema/$schema"),
                projection.limitations().stream().map(CompatibilityLimitation::pointer).toList());
    }

    @Test
    void testDraft07DeclaredWithoutTrailingHashIsSupported() {
        SchemaProjection projection = project("{'$schema':'http://json-schema.org/draft-07/schema',"
                + "'type':'object'}");

        assertTrue(projection.dialectSupported());
        assertTrue(projection.limitations().isEmpty());
    }

    @Test
    void testSupportedDialectIsNotPartOfProjection() {
        SchemaProjection projection = project("{'$schema':'https://json-schema.org/draft/2020-12/schema',"
                + "'type':'object'}");

        assertEquals(json("{'type':'object'}"), projection.projected());
        assertTrue(projection.limitations().isEmpty());
    }

    private static SchemaProjection project(String schema) {
        return SchemaProjector.project(json(schema), "/inputSchema", SchemaSide.CONSUMER);
    }

    private static JsonNode json(String singleQuoted) {
        try {
            return MAPPER.readTree(singleQuoted.replace('\'', '"'));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
