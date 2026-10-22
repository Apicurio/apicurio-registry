package io.apicurio.registry.contracts.rules.cel;

import jakarta.enterprise.context.ApplicationScoped;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.tools.Script;
import org.projectnessie.cel.tools.ScriptException;
import org.projectnessie.cel.tools.ScriptHost;

import java.util.LinkedHashMap;
import java.util.Map;

@ApplicationScoped
public class CelExpressionEvaluator {

    private static final int MAX_CACHE_SIZE = 1000;

    private final ScriptHost scriptHost = ScriptHost.newBuilder().build();
    private final Map<String, Script> scriptCache = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Script> eldest) {
            return size() > MAX_CACHE_SIZE;
        }
    };

    public Object evaluate(String expression, Map<String, Object> variables)
            throws CelEvaluationException {
        Script script = getOrCompile(expression, variables);
        return execute(script, variables);
    }

    private Script getOrCompile(String expression, Map<String, Object> variables) {
        String cacheKey = buildCacheKey(expression, variables);
        synchronized (scriptCache) {
            return scriptCache.computeIfAbsent(cacheKey,
                    k -> compile(expression, variables));
        }
    }

    private Object execute(Script script, Map<String, Object> variables) {
        try {
            return script.execute(Object.class, variables);
        } catch (ScriptException e) {
            throw new CelEvaluationException(
                    "CEL evaluation failed: " + e.getMessage(), e);
        }
    }

    private Script compile(String expression, Map<String, Object> variables) {
        try {
            var builder = scriptHost.buildScript(expression);
            for (String varName : variables.keySet()) {
                builder.withDeclarations(
                        Decls.newVar(varName, Decls.Dyn));
            }
            return builder.build();
        } catch (ScriptException e) {
            throw new CelEvaluationException(
                    "CEL compilation failed: " + e.getMessage(), e);
        }
    }

    private String buildCacheKey(String expression, Map<String, Object> variables) {
        return expression + ":" + String.join(",", variables.keySet());
    }
}
