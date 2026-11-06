package io.apicurio.registry.content.refs.resolve;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Thrown when one or more references could not be resolved.
 */
public class UnresolvedReferencesException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final List<UnresolvedReference> unresolved;

    /**
     * Creates the exception.
     *
     * @param unresolved all unresolved references
     */
    public UnresolvedReferencesException(List<UnresolvedReference> unresolved) {
        super("Unable to resolve references: " + unresolved.stream()
                .map(u -> u.reference() + " (from " + u.fromLocation() + ")")
                .collect(Collectors.joining(", ")));
        this.unresolved = List.copyOf(unresolved);
    }

    /**
     * Returns the unresolved references.
     *
     * @return the unresolved references, in discovery order
     */
    public List<UnresolvedReference> unresolved() {
        return unresolved;
    }
}
