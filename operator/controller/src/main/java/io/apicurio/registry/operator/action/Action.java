package io.apicurio.registry.operator.action;

import io.apicurio.registry.operator.context.CRContext;
import io.apicurio.registry.operator.resource.ResourceKey;
import io.apicurio.registry.operator.state.State;

import java.util.List;

/**
 * Implementations of this interface represent actions to be taken to reconcile a resource. Each action can
 * have an associated {@link State} within the CR context.
 * <p>
 * This class is intended to help with structuring the reconciliation into logical subtasks.
 */
public interface Action<STATE extends State> {

    /**
     * Return a list of resources that this action applies to.
     */
    List<ResourceKey<?>> supports();

    /**
     * Return a class for the state object. Used for bookkeeping.
     */
    Class<STATE> getStateClass();

    /**
     * Initialize the state when a new CR context is created.
     * <p>
     * This method MAY return null if there are multiple actions that use the same state, and an action
     * earlier in the ordering has initialized the state. This is safe because the initialization ordering is
     * relatively static, and does not depend on {@link Action#supports()} and
     * {@link Action#shouldRun(State, CRContext)}, so any uninitialized state results in an error.
     */
    STATE initialize(CRContext crContext);

    /**
     * In addition to {@link Action#supports()}, the action may dynamically decide whether to execute.
     *
     * @return true if the action should be executed during this reconciliation loop
     */
    boolean shouldRun(STATE state, CRContext crContext);

    /**
     * Execute the action based on the following conditions:
     * <ul>
     * <li>{@link Action#supports()}</li>
     * <li>{@link Action#shouldRun(State, CRContext)}</li>
     * </ul>
     */
    void run(STATE state, CRContext crContext);
}
