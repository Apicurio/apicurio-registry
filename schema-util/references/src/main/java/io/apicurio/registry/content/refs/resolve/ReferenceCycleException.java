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
     * @param cycle the source locations forming the cycle, starting at the first source that is
     *        referenced again; that location is repeated at the end (e.g. {@code B -> C -> B}). Sources
     *        that merely lead into the cycle are not included.
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
