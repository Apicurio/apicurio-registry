package io.apicurio.registry.contracts.rules;

import io.apicurio.registry.contracts.rules.cel.CelExpressionEvaluator;
import io.apicurio.registry.contracts.rules.cel.CelRuleExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleExecutionEngineTest {

    private RuleExecutionEngine engine;

    @BeforeEach
    void setUp() {
        CelExpressionEvaluator evaluator = new CelExpressionEvaluator();
        CelRuleExecutor celExecutor = new CelRuleExecutor();

        // Manual injection since no CDI in unit tests
        try {
            var field = CelRuleExecutor.class.getDeclaredField("evaluator");
            field.setAccessible(true);
            field.set(celExecutor, evaluator);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        ContractRuleExecutorFactory factory = new ContractRuleExecutorFactory();
        try {
            var field = ContractRuleExecutorFactory.class.getDeclaredField("executors");
            field.setAccessible(true);
            // Use a simple wrapper that returns our executor
            field.set(factory, new MockInstance<>(List.of(celExecutor)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        engine = new RuleExecutionEngine();
        try {
            var field = RuleExecutionEngine.class.getDeclaredField("executorFactory");
            field.setAccessible(true);
            field.set(engine, factory);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testPassingCondition() {
        RuleDefinition rule = new RuleDefinition();
        rule.setName("positive-amount");
        rule.setKind("CONDITION");
        rule.setType("CEL");
        rule.setExpr("message.amount > 0");
        rule.setOnFailure("ERROR");

        Map<String, Object> record = Map.of("message", Map.of("amount", 100L));
        RuleExecutionResult result = engine.execute(List.of(rule), "WRITE", record);

        assertTrue(result.isPassed());
        assertEquals(1, result.getExecutedRules());
        assertEquals(0, result.getFailedRules());
        assertTrue(result.getViolations().isEmpty());
    }

    @Test
    void testFailingCondition() {
        RuleDefinition rule = new RuleDefinition();
        rule.setName("positive-amount");
        rule.setKind("CONDITION");
        rule.setType("CEL");
        rule.setExpr("message.amount > 0");
        rule.setOnFailure("ERROR");

        Map<String, Object> record = Map.of("message", Map.of("amount", -5L));
        RuleExecutionResult result = engine.execute(List.of(rule), "WRITE", record);

        assertFalse(result.isPassed());
        assertEquals(1, result.getExecutedRules());
        assertEquals(1, result.getFailedRules());
        assertEquals(1, result.getViolations().size());
        assertEquals("positive-amount", result.getViolations().get(0).getRuleName());
    }

    @Test
    void testMultipleRules() {
        RuleDefinition rule1 = new RuleDefinition();
        rule1.setName("positive-amount");
        rule1.setKind("CONDITION");
        rule1.setType("CEL");
        rule1.setExpr("message.amount > 0");
        rule1.setOnFailure("DLQ");
        rule1.setOrderIndex(0);

        RuleDefinition rule2 = new RuleDefinition();
        rule2.setName("valid-status");
        rule2.setKind("CONDITION");
        rule2.setType("CEL");
        rule2.setExpr("message.status == 'active'");
        rule2.setOnFailure("DLQ");
        rule2.setOrderIndex(1);

        Map<String, Object> record = Map.of("message",
                Map.of("amount", 100L, "status", "active"));
        RuleExecutionResult result = engine.execute(
                List.of(rule1, rule2), "WRITE", record);

        assertTrue(result.isPassed());
        assertEquals(2, result.getExecutedRules());
    }

    @Test
    void testErrorStopsExecution() {
        RuleDefinition rule1 = new RuleDefinition();
        rule1.setName("will-fail");
        rule1.setKind("CONDITION");
        rule1.setType("CEL");
        rule1.setExpr("message.amount > 1000");
        rule1.setOnFailure("ERROR");
        rule1.setOrderIndex(0);

        RuleDefinition rule2 = new RuleDefinition();
        rule2.setName("should-not-run");
        rule2.setKind("CONDITION");
        rule2.setType("CEL");
        rule2.setExpr("message.status == 'active'");
        rule2.setOnFailure("ERROR");
        rule2.setOrderIndex(1);

        Map<String, Object> record = Map.of("message",
                Map.of("amount", 50L, "status", "active"));
        RuleExecutionResult result = engine.execute(
                List.of(rule1, rule2), "WRITE", record);

        assertFalse(result.isPassed());
        assertEquals(1, result.getExecutedRules());
    }

    @Test
    void testDlqDoesNotStopExecution() {
        RuleDefinition rule1 = new RuleDefinition();
        rule1.setName("will-fail");
        rule1.setKind("CONDITION");
        rule1.setType("CEL");
        rule1.setExpr("message.amount > 1000");
        rule1.setOnFailure("DLQ");
        rule1.setOrderIndex(0);

        RuleDefinition rule2 = new RuleDefinition();
        rule2.setName("will-pass");
        rule2.setKind("CONDITION");
        rule2.setType("CEL");
        rule2.setExpr("message.status == 'active'");
        rule2.setOnFailure("ERROR");
        rule2.setOrderIndex(1);

        Map<String, Object> record = Map.of("message",
                Map.of("amount", 50L, "status", "active"));
        RuleExecutionResult result = engine.execute(
                List.of(rule1, rule2), "WRITE", record);

        assertFalse(result.isPassed());
        assertEquals(2, result.getExecutedRules());
        assertEquals(1, result.getFailedRules());
    }

    @Test
    void testDisabledRulesSkipped() {
        RuleDefinition rule = new RuleDefinition();
        rule.setName("disabled");
        rule.setKind("CONDITION");
        rule.setType("CEL");
        rule.setExpr("false");
        rule.setDisabled(true);

        Map<String, Object> record = Map.of("message", Map.of("x", 1L));
        RuleExecutionResult result = engine.execute(List.of(rule), "WRITE", record);

        assertTrue(result.isPassed());
        assertEquals(0, result.getExecutedRules());
    }

    @Test
    void testModeFiltering() {
        RuleDefinition writeRule = new RuleDefinition();
        writeRule.setName("write-only");
        writeRule.setKind("CONDITION");
        writeRule.setType("CEL");
        writeRule.setMode("WRITE");
        writeRule.setExpr("message.x > 0");

        RuleDefinition readRule = new RuleDefinition();
        readRule.setName("read-only");
        readRule.setKind("CONDITION");
        readRule.setType("CEL");
        readRule.setMode("READ");
        readRule.setExpr("false");
        readRule.setOnFailure("ERROR");

        Map<String, Object> record = Map.of("message", Map.of("x", 1L));
        RuleExecutionResult result = engine.execute(
                List.of(writeRule, readRule), "WRITE", record);

        assertTrue(result.isPassed());
        assertEquals(1, result.getExecutedRules());
    }

    @Test
    void testWriteReadModeMatchesAll() {
        RuleDefinition rule = new RuleDefinition();
        rule.setName("always");
        rule.setKind("CONDITION");
        rule.setType("CEL");
        rule.setMode("WRITEREAD");
        rule.setExpr("message.x > 0");

        Map<String, Object> record = Map.of("message", Map.of("x", 1L));

        assertTrue(engine.execute(List.of(rule), "WRITE", record).isPassed());
        assertTrue(engine.execute(List.of(rule), "READ", record).isPassed());
    }

    @Test
    void testEmptyRules() {
        Map<String, Object> record = Map.of("message", Map.of("x", 1L));
        RuleExecutionResult result = engine.execute(List.of(), "WRITE", record);

        assertTrue(result.isPassed());
        assertEquals(0, result.getExecutedRules());
        assertNull(result.getTransformedRecord());
    }

    @Test
    void testOrderExecution() {
        RuleDefinition rule2 = new RuleDefinition();
        rule2.setName("second");
        rule2.setKind("CONDITION");
        rule2.setType("CEL");
        rule2.setExpr("message.x > 0");
        rule2.setOrderIndex(2);

        RuleDefinition rule1 = new RuleDefinition();
        rule1.setName("first");
        rule1.setKind("CONDITION");
        rule1.setType("CEL");
        rule1.setExpr("message.x > 0");
        rule1.setOrderIndex(1);

        Map<String, Object> record = Map.of("message", Map.of("x", 1L));
        RuleExecutionResult result = engine.execute(
                List.of(rule2, rule1), "WRITE", record);

        assertTrue(result.isPassed());
        assertEquals(2, result.getExecutedRules());
    }

    /**
     * Minimal mock for CDI Instance — returns a fixed list of executors.
     */
    private static class MockInstance<T> implements jakarta.enterprise.inject.Instance<T> {
        private final List<T> items;

        MockInstance(List<T> items) {
            this.items = items;
        }

        @Override
        public java.util.stream.Stream<T> stream() {
            return items.stream();
        }

        @Override
        public jakarta.enterprise.inject.Instance<T> select(
                java.lang.annotation.Annotation... qualifiers) {
            return this;
        }

        @Override
        public <U extends T> jakarta.enterprise.inject.Instance<U> select(
                Class<U> subtype, java.lang.annotation.Annotation... qualifiers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <U extends T> jakarta.enterprise.inject.Instance<U> select(
                jakarta.enterprise.util.TypeLiteral<U> subtype,
                java.lang.annotation.Annotation... qualifiers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isUnsatisfied() {
            return items.isEmpty();
        }

        @Override
        public boolean isAmbiguous() {
            return false;
        }

        @Override
        public void destroy(T instance) {
        }

        @Override
        public Handle<T> getHandle() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Iterable<? extends Handle<T>> handles() {
            throw new UnsupportedOperationException();
        }

        @Override
        public T get() {
            return items.get(0);
        }

        @Override
        public java.util.Iterator<T> iterator() {
            return items.iterator();
        }

        @Override
        public boolean isResolvable() {
            return items.size() == 1;
        }
    }

    @Test
    void testCreateStandalone() {
        RuleExecutionEngine standalone = RuleExecutionEngine.createStandalone();

        RuleDefinition rule = new RuleDefinition();
        rule.setName("standalone-test");
        rule.setKind("CONDITION");
        rule.setType("CEL");
        rule.setExpr("amount > 0");
        rule.setOnFailure("ERROR");

        Map<String, Object> validRecord = Map.of("amount", 50L);
        RuleExecutionResult passResult = standalone.execute(List.of(rule), "WRITE", validRecord);
        assertTrue(passResult.isPassed());

        Map<String, Object> invalidRecord = Map.of("amount", -1L);
        RuleExecutionResult failResult = standalone.execute(List.of(rule), "WRITE", invalidRecord);
        assertFalse(failResult.isPassed());
        assertEquals(1, failResult.getViolations().size());
    }
}
