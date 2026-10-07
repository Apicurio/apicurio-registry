package io.apicurio.registry.federation;

import io.apicurio.registry.types.RegistryException;

/**
 * Thrown when the search of this registry is still running when the deadline of a federated search
 * has passed. The search is stopped rather than left to run, and as it is this registry's own
 * search that failed, the whole request fails.
 */
public class SearchDeadlineExceededException extends RegistryException {

    private static final long serialVersionUID = 1L;

    public SearchDeadlineExceededException(long deadlineMs) {
        super("The search did not finish within " + deadlineMs + " ms.");
    }
}
