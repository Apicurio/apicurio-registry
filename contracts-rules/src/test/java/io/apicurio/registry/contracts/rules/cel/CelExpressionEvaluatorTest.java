package io.apicurio.registry.contracts.rules.cel;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CelExpressionEvaluatorTest {

    private final CelExpressionEvaluator evaluator = new CelExpressionEvaluator();

    @Test
    void testFieldAccess() {
        Map<String, Object> vars = Map.of("message",
                Map.of("name", "John"));
        assertEquals("John", evaluator.evaluate("message.name", vars));
    }

    @Test
    void testGreaterThan() {
        Map<String, Object> vars = Map.of("message",
                Map.of("amount", 100L));
        assertEquals(true, evaluator.evaluate("message.amount > 0", vars));
        assertEquals(false, evaluator.evaluate("message.amount > 200", vars));
    }

    @Test
    void testEquality() {
        Map<String, Object> vars = Map.of("message",
                Map.of("status", "active"));
        assertEquals(true,
                evaluator.evaluate("message.status == 'active'", vars));
        assertEquals(false,
                evaluator.evaluate("message.status == 'inactive'", vars));
    }

    @Test
    void testLogicalAnd() {
        Map<String, Object> vars = Map.of("message",
                Map.of("age", 25L, "amount", 100L));
        assertEquals(true, evaluator.evaluate(
                "message.age > 0 && message.amount > 0", vars));
        assertEquals(false, evaluator.evaluate(
                "message.age > 0 && message.amount > 200", vars));
    }

    @Test
    void testLogicalOr() {
        Map<String, Object> vars = Map.of("message",
                Map.of("age", 25L));
        assertEquals(true, evaluator.evaluate(
                "message.age > 30 || message.age > 20", vars));
    }

    @Test
    void testNegation() {
        Map<String, Object> vars = Map.of("message",
                Map.of("active", false));
        assertEquals(true, evaluator.evaluate("!message.active", vars));
    }

    @Test
    void testHas() {
        Map<String, Object> vars = Map.of("message",
                Map.of("name", "John"));
        assertEquals(true,
                evaluator.evaluate("has(message.name)", vars));
    }

    @Test
    void testSizeString() {
        Map<String, Object> vars = Map.of("message",
                Map.of("name", "John"));
        assertEquals(4L, evaluator.evaluate("size(message.name)", vars));
    }

    @Test
    void testStringContains() {
        Map<String, Object> vars = Map.of("message",
                Map.of("email", "test@example.com"));
        assertEquals(true,
                evaluator.evaluate("message.email.contains('@')", vars));
    }

    @Test
    void testStringStartsWith() {
        Map<String, Object> vars = Map.of("message",
                Map.of("name", "John"));
        assertEquals(true,
                evaluator.evaluate("message.name.startsWith('Jo')", vars));
    }

    @Test
    void testParenthesizedGrouping() {
        Map<String, Object> vars = Map.of("message",
                Map.of("a", 5L, "b", 10L));
        assertEquals(true, evaluator.evaluate(
                "(message.a > 0) && (message.b < 20)", vars));
    }

    @Test
    void testTernary() {
        Map<String, Object> vars = Map.of("message",
                Map.of("x", 5L));
        assertEquals(5L, evaluator.evaluate(
                "message.x > 0 ? message.x : 0", vars));
    }

    @Test
    void testInvalidExpression() {
        Map<String, Object> vars = Map.of("message", Map.of("x", 1L));
        assertThrows(CelEvaluationException.class,
                () -> evaluator.evaluate("invalid %%% syntax", vars));
    }

    @Test
    void testComparison() {
        Map<String, Object> vars = Map.of("message",
                Map.of("price", 19L, "quantity", 5L));
        assertEquals(true, evaluator.evaluate(
                "message.price > 0 && message.quantity >= 1", vars));
    }

    @Test
    void testNotEqual() {
        Map<String, Object> vars = Map.of("message",
                Map.of("status", "active"));
        assertEquals(true,
                evaluator.evaluate("message.status != 'inactive'", vars));
    }
}
