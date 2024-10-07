package io.apicurio.registry.operator.state;

/**
 * Implementation of {@link State} for actions that do not need to keep any data within the CR context.
 */
public class NoState implements State {

    public static final NoState INSTANCE = new NoState();

    private NoState() {
    }
}
