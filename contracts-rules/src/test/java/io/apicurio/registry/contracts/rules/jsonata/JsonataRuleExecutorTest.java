package io.apicurio.registry.contracts.rules.jsonata;

import io.apicurio.registry.contracts.rules.ContractRuleContext;
import io.apicurio.registry.contracts.rules.ContractRuleResult;
import io.apicurio.registry.contracts.rules.RuleDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonataRuleExecutorTest {

    private JsonataRuleExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new JsonataRuleExecutor();
        executor.evaluator = new JsonataExpressionEvaluator();
    }

    @Test
    void testGetRuleType() {
        assertEquals("JSONATA", executor.getRuleType());
    }

    @Test
    void testConditionPass() {
        RuleDefinition rule = createRule("CONDITION", "price > 0", "ERROR");
        Map<String, Object> record = Map.of("price", 100);
        ContractRuleResult result = executor.execute(new ContractRuleContext(rule, record, null));
        assertTrue(result.isPassed());
    }

    @Test
    void testConditionFail() {
        RuleDefinition rule = createRule("CONDITION", "price > 0", "ERROR");
        Map<String, Object> record = Map.of("price", -5);
        ContractRuleResult result = executor.execute(new ContractRuleContext(rule, record, null));
        assertFalse(result.isPassed());
        assertEquals("ERROR", result.getSuggestedAction());
    }

    @Test
    void testTransformMergeFields() {
        RuleDefinition rule = createRule("TRANSFORM",
                "{ \"fullName\": firstName & ' ' & lastName }", "ERROR");
        Map<String, Object> record = Map.of("firstName", "John", "lastName", "Doe");
        ContractRuleResult result = executor.execute(new ContractRuleContext(rule, record, null));
        assertTrue(result.isPassed());
        assertNotNull(result.getTransformedRecord());
        assertEquals("John Doe", result.getTransformedRecord().get("fullName"));
    }

    @Test
    void testTransformReturnsOriginalWhenNotMap() {
        RuleDefinition rule = createRule("TRANSFORM", "price * 2", "ERROR");
        Map<String, Object> record = Map.of("price", 50);
        ContractRuleResult result = executor.execute(new ContractRuleContext(rule, record, null));
        assertTrue(result.isPassed());
        assertEquals(record, result.getTransformedRecord());
    }

    @Test
    void testBlankExpressionPasses() {
        RuleDefinition rule = createRule("CONDITION", "", "ERROR");
        Map<String, Object> record = Map.of("x", 1);
        ContractRuleResult result = executor.execute(new ContractRuleContext(rule, record, null));
        assertTrue(result.isPassed());
    }

    @Test
    void testNullExpressionPasses() {
        RuleDefinition rule = createRule("CONDITION", null, "ERROR");
        Map<String, Object> record = Map.of("x", 1);
        ContractRuleResult result = executor.execute(new ContractRuleContext(rule, record, null));
        assertTrue(result.isPassed());
    }

    @Test
    void testInvalidExpressionReturnsFail() {
        RuleDefinition rule = createRule("CONDITION", "invalid %%% syntax", "DLQ");
        Map<String, Object> record = Map.of("x", 1);
        ContractRuleResult result = executor.execute(new ContractRuleContext(rule, record, null));
        assertFalse(result.isPassed());
        assertEquals("DLQ", result.getSuggestedAction());
    }

    @Test
    void testConditionWithStringComparison() {
        RuleDefinition rule = createRule("CONDITION", "status = 'active'", "ERROR");
        Map<String, Object> record = Map.of("status", "active");
        ContractRuleResult result = executor.execute(new ContractRuleContext(rule, record, null));
        assertTrue(result.isPassed());
    }

    @Test
    void testTransformSchemaEvolution() {
        RuleDefinition rule = createRule("TRANSFORM",
                "{ \"fullName\": firstName & ' ' & lastName, \"email\": email }", "ERROR");
        Map<String, Object> record = Map.of("firstName", "Jane", "lastName", "Smith",
                "email", "jane@example.com");
        ContractRuleResult result = executor.execute(new ContractRuleContext(rule, record, null));
        assertTrue(result.isPassed());
        assertNotNull(result.getTransformedRecord());
        assertEquals("Jane Smith", result.getTransformedRecord().get("fullName"));
        assertEquals("jane@example.com", result.getTransformedRecord().get("email"));
    }

    private RuleDefinition createRule(String kind, String expr, String onFailure) {
        RuleDefinition rule = new RuleDefinition();
        rule.setName("test-rule");
        rule.setKind(kind);
        rule.setType("JSONATA");
        rule.setMode("WRITE");
        rule.setExpr(expr);
        rule.setOnFailure(onFailure);
        return rule;
    }
}
