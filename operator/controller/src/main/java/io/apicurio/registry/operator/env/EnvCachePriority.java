package io.apicurio.registry.operator.env;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public enum EnvCachePriority {

    // spotless:off
    /**
     * Env. variable is set by the operator.
     */
    OPERATOR(0),
    /**
     * Env. variable is set by the user in the primary resource,
     * and should override any operator-set variables.
     */
    SPEC(1);
    // spotless:on

    private final int value;

    EnvCachePriority(int value) {
        this.value = value;
    }
}
