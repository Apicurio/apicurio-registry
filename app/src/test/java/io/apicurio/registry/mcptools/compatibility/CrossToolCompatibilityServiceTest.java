package io.apicurio.registry.mcptools.compatibility;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossToolCompatibilityServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String CLOSED_PRODUCER_A = "{'type':'object','properties':{'a':{'type':'string'}},"
            + "'required':['a'],'additionalProperties':false}";
    private static final String OPEN_PRODUCER_A = "{'type':'object','properties':{'a':{'type':'string'}},"
            + "'required':['a']}";

    private final CrossToolCompatibilityService service = new CrossToolCompatibilityService();

    @Test
    void testProducerRequiringEveryRequiredInputIsCompatible() {
        PairCompatibility result = compare(CLOSED_PRODUCER_A,
                "{'type':'object','properties':{'a':{'type':'string'}},'required':['a']}");

        assertCompatible(result);
    }

    @Test
    void testUndeclaredProducerOutputIsAcceptedByOpenConsumer() {
        PairCompatibility result = compare(
                "{'type':'object','properties':{'a':{'type':'string'},'x':{'type':'string'}},"
                        + "'required':['a'],'additionalProperties':false}",
                "{'type':'object','properties':{'a':{'type':'string'}},'required':['a']}");

        assertCompatible(result);
    }

    @Test
    void testUndeclaredProducerOutputIsRejectedByClosedConsumer() {
        PairCompatibility result = compare(
                "{'type':'object','properties':{'a':{'type':'string'},'x':{'type':'string'}},"
                        + "'required':['a'],'additionalProperties':false}",
                "{'type':'object','properties':{'a':{'type':'string'}},'required':['a'],"
                        + "'additionalProperties':false}");

        assertIncompatible(result, new CompatibilityReason(ReasonCode.ADDITIONAL_PROPERTY_NOT_ACCEPTED,
                "/outputSchema/properties/x", "/inputSchema/additionalProperties",
                "The producer may emit 'x', which the consumer does not accept"));
    }

    @Test
    void testRequiredInputDeclaredButNotRequiredByProducerIsIncompatible() {
        PairCompatibility result = compare(
                "{'type':'object','properties':{'a':{'type':'string'},'b':{'type':'string'}},"
                        + "'required':['b'],'additionalProperties':false}",
                "{'type':'object','properties':{'a':{'type':'string'}},'required':['a']}");

        assertIncompatible(result, new CompatibilityReason(ReasonCode.REQUIRED_NOT_GUARANTEED,
                "/outputSchema/required", "/inputSchema/required/0",
                "Required input 'a' is declared by the producer but not required"));
    }

    @Test
    void testRequiredInputNotDeclaredByProducerIsIncompatible() {
        PairCompatibility result = compare(
                "{'type':'object','properties':{'b':{'type':'string'}},'additionalProperties':false}",
                "{'type':'object','properties':{'b':{'type':'string'},'a':{'type':'string'}},"
                        + "'required':['b','a']}");

        assertIncompatible(result,
                new CompatibilityReason(ReasonCode.REQUIRED_NOT_GUARANTEED, "/outputSchema",
                        "/inputSchema/required/0",
                        "Required input 'b' is declared by the producer but not required"),
                new CompatibilityReason(ReasonCode.REQUIRED_NOT_GUARANTEED, "/outputSchema",
                        "/inputSchema/required/1", "Required input 'a' is not declared by the producer"));
    }

    @Test
    void testTypeMismatchIsIncompatible() {
        PairCompatibility result = compare(CLOSED_PRODUCER_A,
                "{'type':'object','properties':{'a':{'type':'integer'}}}");

        assertIncompatible(result, new CompatibilityReason(ReasonCode.TYPE_NOT_ACCEPTED,
                "/outputSchema/properties/a/type", "/inputSchema/properties/a/type",
                "The producer's value for 'a' is not accepted by the consumer"));
    }

    @Test
    void testIntegerOutputIsAcceptedAsNumber() {
        PairCompatibility result = compare(
                "{'type':'object','properties':{'n':{'type':'integer'}},'required':['n'],"
                        + "'additionalProperties':false}",
                "{'type':'object','properties':{'n':{'type':'number'}},'required':['n']}");

        assertCompatible(result);
    }

    @Test
    void testNumberOutputIsNotAcceptedAsInteger() {
        PairCompatibility result = compare(
                "{'type':'object','properties':{'n':{'type':'number'}},'required':['n'],"
                        + "'additionalProperties':false}",
                "{'type':'object','properties':{'n':{'type':'integer'}},'required':['n']}");

        assertIncompatible(result, new CompatibilityReason(ReasonCode.TYPE_NOT_ACCEPTED,
                "/outputSchema/properties/n/type", "/inputSchema/properties/n/type",
                "The producer's value for 'n' is not accepted by the consumer"));
    }

    @Test
    void testRootTypeMismatchIsIncompatible() {
        PairCompatibility result = compare("{'type':'array'}", "{'type':'object'}");

        assertIncompatible(result, new CompatibilityReason(ReasonCode.TYPE_NOT_ACCEPTED,
                "/outputSchema/type", "/inputSchema/type",
                "The producer's output type is not accepted by the consumer"));
    }

    @Test
    void testOpenProducerAgainstOptionalTypedInputIsIndeterminate() {
        PairCompatibility result = compare(OPEN_PRODUCER_A,
                "{'type':'object','properties':{'a':{'type':'string'},'b':{'type':'integer'}},"
                        + "'required':['a']}");

        assertEquals(CompatibilityVerdict.INDETERMINATE, result.verdict());
        assertTrue(result.reasons().isEmpty());
        assertEquals(List.of(new CompatibilityLimitation(LimitationCode.PRODUCER_OBJECT_OPEN,
                SchemaSide.PRODUCER, "/outputSchema", "/outputSchema",
                "The outputSchema does not set additionalProperties, so the verdict depends on the"
                        + " producer emitting only the properties it declares")), result.limitations());
    }

    @Test
    void testClosedProducerAgainstOptionalTypedInputIsCompatible() {
        PairCompatibility result = compare(CLOSED_PRODUCER_A,
                "{'type':'object','properties':{'a':{'type':'string'},'b':{'type':'integer'}},"
                        + "'required':['a']}");

        assertCompatible(result);
    }

    @Test
    void testOpenProducerAgainstClosedConsumerIsIndeterminate() {
        PairCompatibility result = compare(OPEN_PRODUCER_A,
                "{'type':'object','properties':{'a':{'type':'string'}},'additionalProperties':false}");

        assertEquals(CompatibilityVerdict.INDETERMINATE, result.verdict());
        assertEquals(List.of(LimitationCode.PRODUCER_OBJECT_OPEN), limitationCodes(result));
    }

    @Test
    void testOpenProducerAgainstInputAcceptingAnyValueIsIndeterminate() {
        PairCompatibility result = compare(OPEN_PRODUCER_A,
                "{'type':'object','properties':{'a':{'type':'string'},'b':{}},'required':['a']}");

        assertEquals(CompatibilityVerdict.INDETERMINATE, result.verdict());
        assertEquals(List.of(LimitationCode.PRODUCER_OBJECT_OPEN), limitationCodes(result));
    }

    @Test
    void testOpenProducerWithMismatchKeptByClosedRunIsIncompatible() {
        PairCompatibility result = compare(OPEN_PRODUCER_A,
                "{'type':'object','properties':{'a':{'type':'integer'},'b':{'type':'integer'}},"
                        + "'required':['a']}");

        assertEquals(CompatibilityVerdict.INCOMPATIBLE, result.verdict());
        assertEquals(List.of(new CompatibilityReason(ReasonCode.TYPE_NOT_ACCEPTED,
                "/outputSchema/properties/a/type", "/inputSchema/properties/a/type",
                "The producer's value for 'a' is not accepted by the consumer")), result.reasons());
        assertEquals(List.of(LimitationCode.PRODUCER_OBJECT_OPEN), limitationCodes(result));
    }

    @Test
    void testExplicitAdditionalPropertiesTrueIsComparedAsDeclared() {
        PairCompatibility result = compare(
                "{'type':'object','properties':{'a':{'type':'string'}},'required':['a'],"
                        + "'additionalProperties':true}",
                "{'type':'object','properties':{'a':{'type':'string'},'b':{'type':'integer'}},"
                        + "'required':['a']}");

        assertIncompatible(result, new CompatibilityReason(ReasonCode.TYPE_NOT_ACCEPTED,
                "/outputSchema/additionalProperties", "/inputSchema/properties/b/type",
                "The producer may emit 'b' with a value the consumer does not accept"));
    }

    @Test
    void testExplicitAdditionalPropertiesSchemaAcceptedByInputIsCompatible() {
        PairCompatibility result = compare(
                "{'type':'object','properties':{'a':{'type':'string'}},'required':['a'],"
                        + "'additionalProperties':{'type':'integer'}}",
                "{'type':'object','properties':{'a':{'type':'string'},'b':{'type':'integer'}},"
                        + "'required':['a']}");

        assertCompatible(result);
    }

    @Test
    void testExplicitAdditionalPropertiesSchemaRejectedByInputIsIncompatible() {
        PairCompatibility result = compare(
                "{'type':'object','properties':{'a':{'type':'string'}},'required':['a'],"
                        + "'additionalProperties':{'type':'integer'}}",
                "{'type':'object','properties':{'a':{'type':'string'},'b':{'type':'string'}},"
                        + "'required':['a']}");

        assertIncompatible(result, new CompatibilityReason(ReasonCode.TYPE_NOT_ACCEPTED,
                "/outputSchema/additionalProperties", "/inputSchema/properties/b/type",
                "The producer may emit 'b' with a value the consumer does not accept"));
    }

    @Test
    void testExplicitAdditionalPropertiesAgainstClosedConsumerIsIncompatible() {
        PairCompatibility result = compare(
                "{'type':'object','properties':{'a':{'type':'string'}},'required':['a'],"
                        + "'additionalProperties':true}",
                "{'type':'object','properties':{'a':{'type':'string'}},'additionalProperties':false}");

        assertIncompatible(result, new CompatibilityReason(ReasonCode.ADDITIONAL_PROPERTY_NOT_ACCEPTED,
                "/outputSchema/additionalProperties", "/inputSchema/additionalProperties",
                "The producer may emit undeclared properties that the consumer does not accept"));
    }

    @Test
    void testConsumerAdditionalPropertiesSchemaRejectsOnlyMismatchingProducerProperties() {
        PairCompatibility result = compare(
                "{'type':'object','properties':{'x':{'type':'string'},'y':{'type':'integer'}},"
                        + "'required':['x','y'],'additionalProperties':false}",
                "{'type':'object','additionalProperties':{'type':'integer'}}");

        assertIncompatible(result, new CompatibilityReason(ReasonCode.ADDITIONAL_PROPERTY_NOT_ACCEPTED,
                "/outputSchema/properties/x", "/inputSchema/additionalProperties",
                "The producer may emit 'x', which the consumer does not accept"));
    }

    @Test
    void testLimitationLeavesIndependentSiblingMismatch() {
        PairCompatibility result = compare(
                "{'type':'object','properties':{'a':{'type':'string'},'b':{'type':'string'}},"
                        + "'required':['a','b'],'additionalProperties':false}",
                "{'type':'object','properties':{'a':{'type':'string','enum':['x']},"
                        + "'b':{'type':'integer'}}}");

        assertEquals(CompatibilityVerdict.INCOMPATIBLE, result.verdict());
        assertEquals(List.of("/inputSchema/properties/b/type"), consumerPointers(result));
        assertEquals(List.of(new CompatibilityLimitation(LimitationCode.UNSUPPORTED_KEYWORD,
                SchemaSide.CONSUMER, "/inputSchema/properties/a", "/inputSchema/properties/a/enum",
                "'enum' is not evaluated yet")), result.limitations());
    }

    @Test
    void testLimitationDoesNotCoverPropertyWhoseNameSharesItsPrefix() {
        PairCompatibility result = compare(
                "{'type':'object','properties':{'a':{'type':'string'},'ab':{'type':'string'}},"
                        + "'required':['a','ab'],'additionalProperties':false}",
                "{'type':'object','properties':{'a':{'type':'string','enum':['x']},"
                        + "'ab':{'type':'integer'}}}");

        assertEquals(CompatibilityVerdict.INCOMPATIBLE, result.verdict());
        assertEquals(List.of("/inputSchema/properties/ab/type"), consumerPointers(result));
    }

    @Test
    void testLimitationInvalidatesMismatchAtItsNode() {
        PairCompatibility result = compare(CLOSED_PRODUCER_A,
                "{'type':'object','properties':{'a':{'type':'integer','enum':[1]}}}");

        assertEquals(CompatibilityVerdict.INDETERMINATE, result.verdict());
        assertTrue(result.reasons().isEmpty());
        assertEquals(List.of(LimitationCode.UNSUPPORTED_KEYWORD), limitationCodes(result));
    }

    @Test
    void testProducerObjectLevelEnumMakesVerdictIndeterminate() {
        PairCompatibility result = compare(
                "{'type':'object','properties':{'y':{'type':['string','integer']}},'required':['y'],"
                        + "'enum':[{'y':'s'}]}",
                "{'type':'object','properties':{'y':{'type':'string'}},'required':['y']}");

        assertEquals(CompatibilityVerdict.INDETERMINATE, result.verdict());
        assertTrue(result.reasons().isEmpty());
        assertTrue(result.limitations().stream()
                .anyMatch(limitation -> "/outputSchema/enum".equals(limitation.pointer())
                        && "/outputSchema".equals(limitation.node())));
    }

    @Test
    void testConsumerPatternPropertiesMakesVerdictIndeterminate() {
        PairCompatibility result = compare(
                "{'type':'object','properties':{'a':{'type':'string'},'x_1':{'type':'string'}},"
                        + "'required':['a'],'additionalProperties':false}",
                "{'type':'object','properties':{'a':{'type':'string'}},"
                        + "'patternProperties':{'^x_':{'type':'string'}},'additionalProperties':false}");

        assertEquals(CompatibilityVerdict.INDETERMINATE, result.verdict());
        assertTrue(result.reasons().isEmpty());
        assertEquals(List.of(new CompatibilityLimitation(LimitationCode.UNSUPPORTED_KEYWORD,
                SchemaSide.CONSUMER, "/inputSchema", "/inputSchema/patternProperties",
                "'patternProperties' is not evaluated yet")), result.limitations());
    }

    @Test
    void testConsumerRefIsLimitationOverItsNodeWhateverItsSiblingsMean() {
        PairCompatibility result = compare(
                "{'type':'object','properties':{'a':{'type':'integer'}},'required':['a'],"
                        + "'additionalProperties':false}",
                "{'type':'object','properties':{'a':{'$ref':'https://example.invalid/a.json',"
                        + "'type':'string'}}}");

        assertEquals(CompatibilityVerdict.INDETERMINATE, result.verdict());
        assertTrue(result.reasons().isEmpty());
        assertEquals(List.of(new CompatibilityLimitation(LimitationCode.UNSUPPORTED_KEYWORD,
                SchemaSide.CONSUMER, "/inputSchema/properties/a", "/inputSchema/properties/a/$ref",
                "'$ref' is not evaluated yet")), result.limitations());
    }

    @Test
    void testPropertiesNamedLikeKeywordsAreComparedAsProperties() {
        PairCompatibility result = compare(
                "{'type':'object','properties':{'format':{'type':'integer'},'enum':{'type':'string'}},"
                        + "'required':['format','enum'],'additionalProperties':false}",
                "{'type':'object','properties':{'format':{'type':'string'},'enum':{'type':'string'}}}");

        assertIncompatible(result, new CompatibilityReason(ReasonCode.TYPE_NOT_ACCEPTED,
                "/outputSchema/properties/format/type", "/inputSchema/properties/format/type",
                "The producer's value for 'format' is not accepted by the consumer"));
    }

    @Test
    void testFormatKeywordOnConsumerIsLimitation() {
        PairCompatibility result = compare(CLOSED_PRODUCER_A,
                "{'type':'object','properties':{'a':{'type':'string','format':'email'}}}");

        assertEquals(CompatibilityVerdict.INDETERMINATE, result.verdict());
        assertEquals(List.of("/inputSchema/properties/a/format"), limitationPointers(result));
    }

    @Test
    void testFormatKeywordOnProducerIsLimitation() {
        PairCompatibility result = compare(
                "{'type':'object','properties':{'a':{'type':'string','format':'email'}},'required':['a'],"
                        + "'additionalProperties':false}",
                "{'type':'object','properties':{'a':{'type':'string'}}}");

        assertEquals(CompatibilityVerdict.INDETERMINATE, result.verdict());
        assertEquals(List.of("/outputSchema/properties/a/format"), limitationPointers(result));
    }

    @Test
    void testFalsePropertySchemaIsNotTreatedAsEmpty() {
        PairCompatibility result = compare(
                "{'type':'object','properties':{'x':{'type':'string'}},'additionalProperties':false}",
                "{'type':'object','properties':{'x':false}}");

        assertIncompatible(result, new CompatibilityReason(ReasonCode.TYPE_NOT_ACCEPTED,
                "/outputSchema/properties/x/type", "/inputSchema/properties/x",
                "The producer's value for 'x' is not accepted by the consumer"));
    }

    @Test
    void testTruePropertySchemaAcceptsAnyValue() {
        PairCompatibility result = compare(
                "{'type':'object','properties':{'x':{'type':'string'}},'additionalProperties':false}",
                "{'type':'object','properties':{'x':true}}");

        assertCompatible(result);
    }

    @Test
    void testDifferenceThatCannotBeAttributedIsComparisonFailure() {
        PairCompatibility result = compare(
                "{'type':'object','properties':{'a':{'type':'string'},'x':false},'required':['a'],"
                        + "'additionalProperties':false}",
                "{'type':'object','properties':{'a':{'type':'string'}},'additionalProperties':false}");

        assertEquals(CompatibilityVerdict.INDETERMINATE, result.verdict());
        assertTrue(result.reasons().isEmpty());
        assertEquals(List.of(new CompatibilityLimitation(LimitationCode.COMPARISON_FAILED,
                SchemaSide.CONSUMER, "/inputSchema", "/inputSchema",
                "A mismatch reported by the comparison engine could not be attributed to a property")),
                result.limitations());
    }

    @Test
    void testTypeGivenAsArrayIsLimitation() {
        PairCompatibility result = compare(
                "{'type':'object','properties':{'a':{'type':['string','null']}},'required':['a'],"
                        + "'additionalProperties':false}",
                "{'type':'object','properties':{'a':{'type':'string'}},'required':['a']}");

        assertEquals(CompatibilityVerdict.INDETERMINATE, result.verdict());
        assertTrue(result.reasons().isEmpty());
        assertEquals(List.of(new CompatibilityLimitation(LimitationCode.UNSUPPORTED_KEYWORD,
                SchemaSide.PRODUCER, "/outputSchema/properties/a", "/outputSchema/properties/a/type",
                "'type' given as an array is not evaluated yet")), result.limitations());
    }

    @Test
    void testNestedStructureIsDepthLimitWhileOtherMismatchStillCounts() {
        PairCompatibility result = compare(
                "{'type':'object','properties':{'u':{'type':'object'},'b':{'type':'string'}},"
                        + "'required':['u','b'],'additionalProperties':false}",
                "{'type':'object','properties':{'u':{'type':'object','properties':{'id':{'type':'string'}}},"
                        + "'b':{'type':'integer'}}}");

        assertEquals(CompatibilityVerdict.INCOMPATIBLE, result.verdict());
        assertEquals(List.of("/inputSchema/properties/b/type"), consumerPointers(result));
        assertEquals(List.of(new CompatibilityLimitation(LimitationCode.DEPTH_LIMIT_REACHED,
                SchemaSide.CONSUMER, "/inputSchema/properties/u", "/inputSchema/properties/u/properties",
                "Nested 'properties' is not evaluated yet")), result.limitations());
    }

    @Test
    void testUnsupportedConsumerDialectCoversWholeSchema() {
        PairCompatibility result = compare(CLOSED_PRODUCER_A,
                "{'$schema':'https://example.invalid/custom-dialect','type':'object',"
                        + "'properties':{'a':{'type':'integer'}}}");

        assertEquals(CompatibilityVerdict.INDETERMINATE, result.verdict());
        assertTrue(result.reasons().isEmpty());
        assertEquals(List.of(new CompatibilityLimitation(LimitationCode.UNSUPPORTED_DIALECT,
                SchemaSide.CONSUMER, "/inputSchema", "/inputSchema/$schema",
                "JSON Schema dialect 'https://example.invalid/custom-dialect' is not supported")),
                result.limitations());
    }

    @Test
    void testNonTextualDialectIsUnsupported() {
        PairCompatibility result = compare(CLOSED_PRODUCER_A, "{'$schema':7,'type':'object'}");

        assertEquals(CompatibilityVerdict.INDETERMINATE, result.verdict());
        assertEquals(List.of(new CompatibilityLimitation(LimitationCode.UNSUPPORTED_DIALECT,
                SchemaSide.CONSUMER, "/inputSchema", "/inputSchema/$schema",
                "'$schema' must be a string")), result.limitations());
    }

    @Test
    void testSupportedDeclaredDialectIsCompared() {
        PairCompatibility result = compare(CLOSED_PRODUCER_A,
                "{'$schema':'http://json-schema.org/draft-07/schema#','type':'object',"
                        + "'properties':{'a':{'type':'string'}},'required':['a']}");

        assertCompatible(result);
    }

    @Test
    void testUnsupportedProducerDialectPreventsAnyMatch() {
        PreparedProducer producer = service.prepareProducer(tool("outputSchema",
                "{'$schema':'https://example.invalid/custom-dialect','type':'object'}"));

        assertFalse(producer.canMatch());
        assertEquals(List.of(LimitationCode.UNSUPPORTED_DIALECT), codes(producer.limitations()));
        PairCompatibility result = service.compare(producer, tool("inputSchema", "{'type':'object'}"));
        assertEquals(CompatibilityVerdict.INDETERMINATE, result.verdict());
        assertEquals(List.of(LimitationCode.UNSUPPORTED_DIALECT), limitationCodes(result));
    }

    @Test
    void testConsumerWithoutPropertiesAcceptsObjectProducer() {
        PairCompatibility result = compare(CLOSED_PRODUCER_A, "{'type':'object'}");

        assertCompatible(result);
    }

    @Test
    void testClosedConsumerWithoutPropertiesRejectsDeclaredProperty() {
        PairCompatibility result = compare(
                "{'type':'object','properties':{'a':{'type':'string'}},'additionalProperties':false}",
                "{'type':'object','additionalProperties':false}");

        assertIncompatible(result, new CompatibilityReason(ReasonCode.ADDITIONAL_PROPERTY_NOT_ACCEPTED,
                "/outputSchema/properties/a", "/inputSchema/additionalProperties",
                "The producer may emit 'a', which the consumer does not accept"));
    }

    @Test
    void testProducerWithoutPropertiesIsComparedAsDeclared() {
        PreparedProducer producer = service.prepareProducer(tool("outputSchema", "{'type':'object'}"));
        PairCompatibility result = service.compare(producer, tool("inputSchema",
                "{'type':'object','properties':{'a':{'type':'string'}},'required':['a']}"));

        assertTrue(producer.canMatch());
        assertEquals(CompatibilityVerdict.INCOMPATIBLE, result.verdict());
        assertTrue(result.reasons().contains(new CompatibilityReason(ReasonCode.REQUIRED_NOT_GUARANTEED,
                "/outputSchema", "/inputSchema/required/0",
                "Required input 'a' is not declared by the producer")));
        assertTrue(result.limitations().isEmpty());
    }

    @Test
    void testAnnotationsDoNotChangeVerdict() {
        PairCompatibility result = compare(
                "{'type':'object','title':'Out','description':'d','$comment':'c','examples':[{'a':'x'}],"
                        + "'properties':{'a':{'type':'string','title':'A','description':'d','default':'x',"
                        + "'examples':['x'],'$comment':'c'}},'required':['a'],'additionalProperties':false}",
                "{'type':'object','title':'In','properties':{'a':{'type':'string','description':'d'}},"
                        + "'required':['a']}");

        assertCompatible(result);
    }

    @Test
    void testPointersEscapePropertyNames() {
        PairCompatibility result = compare(
                "{'type':'object','properties':{'a/b~c':{'type':'string'}},'required':['a/b~c'],"
                        + "'additionalProperties':false}",
                "{'type':'object','properties':{'a/b~c':{'type':'integer'}}}");

        assertIncompatible(result, new CompatibilityReason(ReasonCode.TYPE_NOT_ACCEPTED,
                "/outputSchema/properties/a~1b~0c/type", "/inputSchema/properties/a~1b~0c/type",
                "The producer's value for 'a/b~c' is not accepted by the consumer"));
    }

    @Test
    void testToolWithoutOutputSchemaCannotMatch() {
        PreparedProducer producer = service.prepareProducer(tool("inputSchema", "{'type':'object'}"));

        assertFalse(producer.canMatch());
        assertEquals(List.of(new CompatibilityLimitation(LimitationCode.SOURCE_HAS_NO_OUTPUT_SCHEMA,
                SchemaSide.PRODUCER, "/outputSchema", "/outputSchema", "The tool declares no outputSchema")),
                producer.limitations());
        assertEquals(CompatibilityVerdict.INDETERMINATE,
                service.compare(producer, tool("inputSchema", "{'type':'object'}")).verdict());
    }

    @Test
    void testUnreadableOutputSchemaCannotMatch() {
        PreparedProducer producer = service.prepareProducer(tool("outputSchema", "{'type':'strng'}"));

        assertFalse(producer.canMatch());
        assertEquals(List.of(LimitationCode.COMPARISON_FAILED), codes(producer.limitations()));
    }

    @Test
    void testUnparseableProducerCannotMatch() {
        PreparedProducer producer = service.unreadableProducer();

        assertFalse(producer.canMatch());
        assertEquals(List.of(LimitationCode.COMPARISON_FAILED), codes(producer.limitations()));
    }

    @Test
    void testUnreadableInputSchemaIsIndeterminate() {
        PairCompatibility result = compare(CLOSED_PRODUCER_A, "{'type':'object','properties':[]}");

        assertEquals(CompatibilityVerdict.INDETERMINATE, result.verdict());
        assertEquals(List.of(LimitationCode.COMPARISON_FAILED), limitationCodes(result));
        assertEquals(SchemaSide.CONSUMER, result.limitations().get(0).side());
    }

    @Test
    void testMissingInputSchemaIsIndeterminate() {
        PreparedProducer producer = service.prepareProducer(tool("outputSchema", CLOSED_PRODUCER_A));
        PairCompatibility result = service.compare(producer, json("{'name':'consumer'}"));

        assertEquals(CompatibilityVerdict.INDETERMINATE, result.verdict());
        assertEquals(List.of(new CompatibilityLimitation(LimitationCode.COMPARISON_FAILED,
                SchemaSide.CONSUMER, "/inputSchema", "/inputSchema",
                "The tool declares no inputSchema object")), result.limitations());
    }

    @Test
    void testUnparseableConsumerIsIndeterminate() {
        PreparedProducer producer = service.prepareProducer(tool("outputSchema", CLOSED_PRODUCER_A));
        PairCompatibility result = service.unreadableConsumer(producer);

        assertEquals(CompatibilityVerdict.INDETERMINATE, result.verdict());
        assertEquals(List.of(LimitationCode.COMPARISON_FAILED), limitationCodes(result));
    }

    private PairCompatibility compare(String outputSchema, String inputSchema) {
        PreparedProducer producer = service.prepareProducer(tool("outputSchema", outputSchema));
        return service.compare(producer, tool("inputSchema", inputSchema));
    }

    private static JsonNode tool(String schemaField, String schema) {
        return json("{'name':'tool','" + schemaField + "':" + schema + "}");
    }

    private static JsonNode json(String singleQuoted) {
        try {
            return MAPPER.readTree(singleQuoted.replace('\'', '"'));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static void assertCompatible(PairCompatibility result) {
        assertEquals(new PairCompatibility(CompatibilityVerdict.COMPATIBLE, List.of(), List.of()), result);
    }

    private static void assertIncompatible(PairCompatibility result, CompatibilityReason... reasons) {
        assertEquals(new PairCompatibility(CompatibilityVerdict.INCOMPATIBLE, List.of(reasons), List.of()),
                result);
    }

    private static List<String> consumerPointers(PairCompatibility result) {
        return result.reasons().stream().map(CompatibilityReason::consumerPointer).toList();
    }

    private static List<String> limitationPointers(PairCompatibility result) {
        return result.limitations().stream().map(CompatibilityLimitation::pointer).toList();
    }

    private static List<LimitationCode> limitationCodes(PairCompatibility result) {
        return codes(result.limitations());
    }

    private static List<LimitationCode> codes(List<CompatibilityLimitation> limitations) {
        return limitations.stream().map(CompatibilityLimitation::code).toList();
    }
}
