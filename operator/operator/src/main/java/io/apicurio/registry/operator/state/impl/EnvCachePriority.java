package io.apicurio.registry.operator.state.impl;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public enum EnvCachePriority {

    // spotless:off
    /**
     * Env. variable is set by the operator.
     */
    OPERATOR_LOW(0),
    /**
     * Env. variable is set by the user in the primary resource,
     * and should override any operator-set variables.
     */
    SPEC_HIGH(1);
    // spotless:on

    private final int priority;

    EnvCachePriority(int priority) {
        this.priority = priority;
    }
}
