package io.apicurio.registry.operator.state.impl;

import io.apicurio.registry.operator.OperatorException;
import io.apicurio.registry.operator.state.State;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.*;

import static java.util.Objects.requireNonNull;
import static lombok.AccessLevel.PRIVATE;

public abstract class EnvCache implements State {

    private Map<String, EnvVarEntry> env = new HashMap<>();

    public EnvCache add(String key, String value, EnvCachePriority priority, String... dependencies) {
        requireNonNull(value);
        return add(new EnvVarBuilder().withName(key).withValue(value).build(), priority, dependencies);
    }

    public EnvCache add(EnvVar value, EnvCachePriority priority, String... dependencies) {
        requireNonNull(value);
        var e = EnvVarEntry.builder().priority(priority).var(value).dependencies(Arrays.asList(dependencies))
                .build();
        return add(e);
    }

    public EnvCache add(EnvVarEntry e) {
        requireNonNull(e.getPriority());
        requireNonNull(e.getVar());
        requireNonNull(e.getVar().getName());
        var key = e.getVar().getName();
        var prev = env.get(key);
        if (prev == null || prev.getPriority().getPriority() <= e.getPriority().getPriority()) {
            env.put(key, e);
        }
        return this;
    }

    private void recursive(List<EnvVar> res, EnvVarEntry current, int depth) {
        if (depth > env.size()) {
            throw new OperatorException(
                    """
                            Cycle detected during the processing of environment variables (at %s), make sure that every env. variable is define once. \
                            Explanation: Ordering of the env. variables is significant, because some variables can reference others using interpolation. \
                            As a result, the operator tries to keep the order consistent (e.g. as defined in the CR). \
                            This error occurs when the operator could not order the variables correctly."""
                            .formatted(current.getVar().getName()));
        }
        if (!res.contains(current.getVar())) {
            for (String d : current.getDependencies()) {
                recursive(res, env.get(d), depth + 1);
            }
            res.add(current.getVar());
        }
    }

    public List<EnvVar> getEnv() {
        var res = new ArrayList<EnvVar>(env.size());
        env.values().forEach(e -> recursive(res, e, 1));
        return res;
    }

    public List<EnvVar> getEnvAndReset() {
        var res = getEnv();
        reset();
        return res;
    }

    public void reset() {
        env.clear();
    }

    @Builder
    @AllArgsConstructor(access = PRIVATE)
    @Getter
    public static class EnvVarEntry {
        EnvVar var;
        EnvCachePriority priority;
        List<String> dependencies;
    }
}
