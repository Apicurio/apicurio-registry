package io.apicurio.registry.operator.env;

import io.apicurio.registry.operator.OperatorException;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;

import java.util.*;

import static java.util.Objects.requireNonNull;

/**
 * This class manages a collection of env. variables that should be applied to a Deployment.
 */
public class EnvCache {

    private Map<String, EnvCacheEntry> env = new HashMap<>();

    public void addFromPrimary(List<EnvVar> env) {
        requireNonNull(env);
        String prev = null;
        for (EnvVar e : env) {
            add(e, EnvCachePriority.SPEC, prev != null ? new String[] { prev } : new String[] {});
            prev = e.getName();
        }
    }

    public EnvCache add(String key, String value, EnvCachePriority priority, String... dependencies) {
        requireNonNull(value);
        return add(new EnvVarBuilder().withName(key).withValue(value).build(), priority, dependencies);
    }

    public EnvCache add(EnvVar value, EnvCachePriority priority, String... dependencies) {
        requireNonNull(value);
        // spotless:off
        var e = EnvCacheEntry.builder()
                .priority(priority)
                .var(value)
                .dependencies(Arrays.asList(dependencies))
                .build();
        // spotless:on
        return add(e);
    }

    public EnvCache add(EnvCacheEntry e) {
        requireNonNull(e.getPriority());
        requireNonNull(e.getVar());
        requireNonNull(e.getVar().getName());
        var key = e.getVar().getName();
        var prev = env.get(key);
        if (prev == null || prev.getPriority().getValue() <= e.getPriority().getValue()) {
            env.put(key, e);
        }
        return this;
    }

    private void recursiveDependencyOrdering(List<EnvVar> res, EnvCacheEntry current, int depth) {
        if (depth > env.size()) {
            // spotless:off
            throw new OperatorException("""
                    Cycle detected during the processing of environment variables (at %s), make sure that every env. variable is define once. \
                    Explanation: Ordering of the env. variables is significant, because some variables can reference others using interpolation. \
                    As a result, the operator tries to keep the order consistent (e.g. as defined in the CR). \
                    This error occurs when the operator could not order the variables correctly.""".formatted(current.getVar().getName()));
            // spotless:on
        }
        if (!res.contains(current.getVar())) {
            for (String d : current.getDependencies()) {
                recursiveDependencyOrdering(res, env.get(d), depth + 1);
            }
            res.add(current.getVar());
        }
    }

    public List<EnvVar> getEnv() {
        var res = new ArrayList<EnvVar>(env.size());
        env.values().forEach(e -> recursiveDependencyOrdering(res, e, 1));
        return res;
    }

    public void reset() {
        env.clear();
    }
}
