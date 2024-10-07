package io.apicurio.registry.operator.state.impl;

import io.apicurio.registry.operator.resource.ResourceKey;
import io.apicurio.registry.operator.state.State;
import lombok.Getter;
import lombok.ToString;

import java.util.HashMap;
import java.util.Map;

@Getter
@ToString
public class ExampleState implements State {

    private final Map<ResourceKey<?>, Long> counters = new HashMap<>();

    public long next(ResourceKey<?> key) {
        return counters.compute(key, (k, v) -> (v == null) ? 1 : v + 1);
    }
}
