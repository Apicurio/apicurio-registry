package io.apicurio.registry.contracts.rules.cel;

import io.apicurio.registry.contracts.rules.ContractRuleContext;
import io.apicurio.registry.contracts.rules.ContractRuleResult;
import io.apicurio.registry.contracts.rules.RuleDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CelFieldRuleExecutorTest {

    private CelFieldRuleExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new CelFieldRuleExecutor(new CelExpressionEvaluator());
    }

    @Test
    void testGetRuleType() {
        assertEquals("CEL_FIELD", executor.getRuleType());
    }

    @Test
    void testConditionPassFlatField() {
        RuleDefinition rule = createRule("CONDITION", "value.contains('@')", "ERROR", Set.of("EMAIL"));
        Map<String, Object> dataRecord = Map.of("email", "user@example.com");
        Map<String, Set<String>> fieldTags = Map.of("email", Set.of("EMAIL"));

        ContractRuleResult result = executor.execute(new ContractRuleContext(rule, dataRecord, fieldTags));
        assertTrue(result.isPassed());
    }

    @Test
    void testConditionFailFlatField() {
        RuleDefinition rule = createRule("CONDITION", "value.contains('@')", "ERROR", Set.of("EMAIL"));
        Map<String, Object> dataRecord = Map.of("email", "invalid_email");
        Map<String, Set<String>> fieldTags = Map.of("email", Set.of("EMAIL"));

        ContractRuleResult result = executor.execute(new ContractRuleContext(rule, dataRecord, fieldTags));
        assertFalse(result.isPassed());
        assertEquals("ERROR", result.getSuggestedAction());
        assertTrue(result.getMessage().contains("email"));
    }

    @Test
    void testConditionNestedField() {
        RuleDefinition rule = createRule("CONDITION", "size(value) >= 3", "ERROR", Set.of("PII"));
        Map<String, Object> dataRecord = Map.of("user", Map.of("profile", Map.of("ssn", "12")));
        Map<String, Set<String>> fieldTags = Map.of("user.profile.ssn", Set.of("PII"));

        ContractRuleResult result = executor.execute(new ContractRuleContext(rule, dataRecord, fieldTags));
        assertFalse(result.isPassed());
        assertTrue(result.getMessage().contains("user.profile.ssn"));
    }

    @Test
    void testConditionArrayField() {
        RuleDefinition rule = createRule("CONDITION", "value.startsWith('+')", "ERROR", Set.of("PHONE"));
        Map<String, Object> dataRecord = Map.of("phones", List.of("+123456", "987654"));
        Map<String, Set<String>> fieldTags = Map.of("phones[]", Set.of("PHONE"));

        ContractRuleResult result = executor.execute(new ContractRuleContext(rule, dataRecord, fieldTags));
        assertFalse(result.isPassed());
        assertTrue(result.getMessage().contains("phones[]"));
    }

    @Test
    void testNullFieldValueEvaluated() {
        RuleDefinition rule = createRule("CONDITION", "value != null", "ERROR", Set.of("REQUIRED"));
        Map<String, Object> dataRecord = new HashMap<>();
        dataRecord.put("email", null);
        Map<String, Set<String>> fieldTags = Map.of("email", Set.of("REQUIRED"));

        ContractRuleResult result = executor.execute(new ContractRuleContext(rule, dataRecord, fieldTags));
        assertFalse(result.isPassed());
        assertTrue(result.getMessage().contains("email"));
    }

    @Test
    void testNoMatchReturnsPass() {
        RuleDefinition rule = createRule("CONDITION", "value.contains('@')", "ERROR", Set.of("EMAIL"));
        Map<String, Object> dataRecord = Map.of("phone", "123456");
        Map<String, Set<String>> fieldTags = Map.of("phone", Set.of("PHONE"));

        ContractRuleResult result = executor.execute(new ContractRuleContext(rule, dataRecord, fieldTags));
        assertTrue(result.isPassed());
    }

    @Test
    void testNullFieldTagsReturnsPass() {
        RuleDefinition rule = createRule("CONDITION", "value.contains('@')", "ERROR", Set.of("EMAIL"));
        Map<String, Object> dataRecord = Map.of("email", "test");

        ContractRuleResult result = executor.execute(new ContractRuleContext(rule, dataRecord, null));
        assertTrue(result.isPassed());
    }

    @Test
    void testTransformFlatField() {
        RuleDefinition rule = createRule("TRANSFORM", "'MASKED-' + value", "NONE", Set.of("SSN"));
        Map<String, Object> dataRecord = Map.of("ssn", "123-45-6789");
        Map<String, Set<String>> fieldTags = Map.of("ssn", Set.of("SSN"));

        ContractRuleResult result = executor.execute(new ContractRuleContext(rule, dataRecord, fieldTags));
        assertTrue(result.isPassed());
        assertNotNull(result.getTransformedRecord());
        assertEquals("MASKED-123-45-6789", result.getTransformedRecord().get("ssn"));
    }

    @Test
    void testTransformFailureHonorsOnFailure() {
        RuleDefinition rule = createRule("TRANSFORM", "invalid_syntax_expr", "ERROR", Set.of("SSN"));
        Map<String, Object> dataRecord = Map.of("ssn", "123-45-6789");
        Map<String, Set<String>> fieldTags = Map.of("ssn", Set.of("SSN"));

        ContractRuleResult result = executor.execute(new ContractRuleContext(rule, dataRecord, fieldTags));
        assertFalse(result.isPassed());
        assertEquals("ERROR", result.getSuggestedAction());
    }

    @Test
    void testPathMatchingArrayAndMap() {
        RuleDefinition rule = createRule("CONDITION", "value != null", "ERROR", Set.of("SENSITIVE"));
        Map<String, Object> dataRecord = Map.of(
                "phones", List.of("12345"),
                "metadata", Map.of("k1", "v1")
        );
        Map<String, Set<String>> fieldTags = Map.of(
                "phones[]", Set.of("SENSITIVE"),
                "metadata.values", Set.of("SENSITIVE")
        );

        ContractRuleResult result = executor.execute(new ContractRuleContext(rule, dataRecord, fieldTags));
        assertTrue(result.isPassed());
    }

    @Test
    void testTransformImmutableInputMap() {
        RuleDefinition rule = createRule("TRANSFORM", "'TRANSFORMED-' + value", "NONE", Set.of("NAME"));
        Map<String, Object> dataRecord = Map.of("name", "john");
        Map<String, Set<String>> fieldTags = Map.of("name", Set.of("NAME"));

        ContractRuleResult result = executor.execute(new ContractRuleContext(rule, dataRecord, fieldTags));
        assertTrue(result.isPassed());
        assertEquals("TRANSFORMED-john", result.getTransformedRecord().get("name"));
        // Original immutable map must be untouched
        assertEquals("john", dataRecord.get("name"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testTransformDeeplyNested3LevelsParentThreading() {
        RuleDefinition rule = createRule("TRANSFORM", "'CITY-' + value", "NONE", Set.of("CITY"));
        Map<String, Object> originalRecord = Map.of(
                "level1", Map.of(
                        "level2", Map.of(
                                "level3", Map.of(
                                        "city", "boston"
                                )
                        )
                )
        );
        Map<String, Set<String>> fieldTags = Map.of("level1.level2.level3.city", Set.of("CITY"));

        ContractRuleResult result = executor.execute(new ContractRuleContext(rule, originalRecord, fieldTags));
        assertTrue(result.isPassed());

        Map<String, Object> transformed = result.getTransformedRecord();
        assertNotNull(transformed);
        assertNotSame(originalRecord, transformed);

        Map<String, Object> l1 = (Map<String, Object>) transformed.get("level1");
        Map<String, Object> l2 = (Map<String, Object>) l1.get("level2");
        Map<String, Object> l3 = (Map<String, Object>) l2.get("level3");
        assertEquals("CITY-boston", l3.get("city"));

        // Verify original immutability
        Map<String, Object> origL1 = (Map<String, Object>) originalRecord.get("level1");
        Map<String, Object> origL2 = (Map<String, Object>) origL1.get("level2");
        Map<String, Object> origL3 = (Map<String, Object>) origL2.get("level3");
        assertEquals("boston", origL3.get("city"));
    }

    @Test
    void testNullRuleTagsMatchesNothing() {
        RuleDefinition rule = createRule("CONDITION", "value != null", "ERROR", null);
        Map<String, Object> dataRecord = Map.of("name", "john");
        Map<String, Set<String>> fieldTags = Map.of("name", Set.of("NAME"));

        ContractRuleResult result = executor.execute(new ContractRuleContext(rule, dataRecord, fieldTags));
        assertTrue(result.isPassed());
    }

    private RuleDefinition createRule(String kind, String expr, String onFailure, Set<String> tags) {
        RuleDefinition rule = new RuleDefinition();
        rule.setName("test-rule");
        rule.setKind(kind);
        rule.setType("CEL_FIELD");
        rule.setMode("WRITE");
        rule.setExpr(expr);
        rule.setTags(tags);
        rule.setOnFailure(onFailure);
        return rule;
    }
}
