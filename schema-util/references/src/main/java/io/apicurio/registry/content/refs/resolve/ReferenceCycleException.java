package io.apicurio.registry.content.refs.resolve;

import java.util.List;

/**
 * Thrown when schema references form a cycle.
 */
public class ReferenceCycleException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final List<String> cycle;

    /**
     * Creates the exception.
     *
     * @param cycle the source locations forming the cycle; the first location is repeated at the end
     */
    public ReferenceCycleException(List<String> cycle) {
        super("Artifact reference loop detected: " + String.join(" -> ", cycle));
        this.cycle = List.copyOf(cycle);
    }

    /**
     * Returns the source locations forming the cycle.
     *
     * @return the cycle path
     */
    public List<String> cycle() {
        return cycle;
    }
}
