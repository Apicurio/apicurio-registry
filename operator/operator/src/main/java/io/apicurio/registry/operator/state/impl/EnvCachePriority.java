package io.apicurio.registry.operator.state.impl;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public enum EnvCachePriority {

    // spotless:off
    OPERATOR_LOW(0),
    SPEC_HIGH(1);
    // spotless:on

    private final int priority;

    EnvCachePriority(int priority) {
        this.priority = priority;
    }
}
