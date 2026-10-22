package io.apicurio.registry.contracts.rules.jsonata;

import static com.dashjoin.jsonata.Jsonata.jsonata;

import com.dashjoin.jsonata.Jsonata;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.LinkedHashMap;
import java.util.Map;

@ApplicationScoped
public class JsonataExpressionEvaluator {

    private static final int MAX_CACHE_SIZE = 1000;

    private final Map<String, Jsonata> expressionCache = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Jsonata> eldest) {
            return size() > MAX_CACHE_SIZE;
        }
    };

    public Object evaluate(String expression, Object input) throws JsonataEvaluationException {
        Jsonata compiled = getOrCompile(expression);
        return execute(compiled, input);
    }

    private Jsonata getOrCompile(String expression) {
        synchronized (expressionCache) {
            return expressionCache.computeIfAbsent(expression, this::compile);
        }
    }

    private Jsonata compile(String expression) {
        try {
            return jsonata(expression);
        } catch (Exception e) {
            throw new JsonataEvaluationException(
                    "JSONata compilation failed: " + e.getMessage(), e);
        }
    }

    private Object execute(Jsonata compiled, Object input) {
        try {
            return compiled.evaluate(input);
        } catch (Exception e) {
            throw new JsonataEvaluationException(
                    "JSONata evaluation failed: " + e.getMessage(), e);
        }
    }
}
