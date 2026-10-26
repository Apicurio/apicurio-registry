package io.apicurio.registry.references;

import io.apicurio.registry.types.RegistryException;

/**
 * Thrown by {@link ReferenceGraphWalker} when a reference graph is deeper than
 * {@code apicurio.storage.references.max-depth} allows.
 * <p>
 * The walker fails outright rather than truncating the walk and logging a warning, which is what the
 * walker this replaces did. A truncated graph is a silently wrong graph: callers resolving references for
 * compatibility checking or canonicalization would see an incomplete set of dependencies with no
 * indication anything was cut off. Failing is the honest answer, even though it is a behaviour change for
 * graphs deeper than the configured limit (accepted in the design, #10635, open question 3).
 */
public class ReferenceDepthExceededException extends RegistryException {

    private static final long serialVersionUID = 1L;

    public ReferenceDepthExceededException(int maxDepth) {
        super("Reference graph exceeds the maximum allowed depth of " + maxDepth + ".");
    }
}
