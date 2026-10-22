package io.apicurio.registry.contracts.rules.jsonata;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonataExpressionEvaluatorTest {

    private final JsonataExpressionEvaluator evaluator = new JsonataExpressionEvaluator();

    @Test
    void testFieldAccess() {
        Map<String, Object> data = Map.of("name", "John", "age", 30);
        assertEquals("John", evaluator.evaluate("name", data));
    }

    @Test
    void testNestedFieldAccess() {
        Map<String, Object> data = Map.of("user", Map.of("name", "John"));
        assertEquals("John", evaluator.evaluate("user.name", data));
    }

    @Test
    void testComparison() {
        Map<String, Object> data = Map.of("price", 100);
        assertEquals(true, evaluator.evaluate("price > 0", data));
        assertEquals(false, evaluator.evaluate("price > 200", data));
    }

    @Test
    void testStringConcatenation() {
        Map<String, Object> data = Map.of("firstName", "John", "lastName", "Doe");
        assertEquals("John Doe", evaluator.evaluate("firstName & ' ' & lastName", data));
    }

    @Test
    void testTransformExpression() {
        Map<String, Object> data = Map.of("firstName", "John", "lastName", "Doe");
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) evaluator.evaluate(
                "{ \"fullName\": firstName & ' ' & lastName }", data);
        assertEquals("John Doe", result.get("fullName"));
    }

    @Test
    void testArraySum() {
        Map<String, Object> data = Map.of("values", List.of(1, 2, 3, 4, 5));
        Object result = evaluator.evaluate("$sum(values)", data);
        assertTrue(result instanceof Number);
        assertEquals(15, ((Number) result).intValue());
    }

    @Test
    void testConditionalExpression() {
        Map<String, Object> data = Map.of("age", 25);
        assertEquals("adult", evaluator.evaluate(
                "age >= 18 ? 'adult' : 'minor'", data));
    }

    @Test
    void testMissingFieldReturnsNull() {
        Map<String, Object> data = Map.of("name", "John");
        assertNull(evaluator.evaluate("nonExistent", data));
    }

    @Test
    void testInvalidExpression() {
        Map<String, Object> data = Map.of("x", 1);
        assertThrows(JsonataEvaluationException.class,
                () -> evaluator.evaluate("invalid %%% syntax", data));
    }

    @Test
    void testCachingReturnsSameResult() {
        Map<String, Object> data = Map.of("x", 10);
        Object r1 = evaluator.evaluate("x > 0", data);
        Object r2 = evaluator.evaluate("x > 0", data);
        assertEquals(r1, r2);
    }

    @Test
    void testObjectConstruction() {
        Map<String, Object> data = Map.of("price", 100, "quantity", 3);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) evaluator.evaluate(
                "{ \"total\": price * quantity }", data);
        assertEquals(300, ((Number) result.get("total")).intValue());
    }
}
