package io.apicurio.registry.operator.state;

import io.apicurio.registry.operator.action.Action;

/**
 * Interface to represent {@link Action} state.
 * <p>
 * See {@link NoState} for the default implementation.
 */
public interface State {

    /**
     * This method is executed after each reconciliation, and can be used, for example to reset the state to a
     * default value.
     */
    default void afterReconciliation() {
    }
}
