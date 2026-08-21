package io.apicurio.registry.rules.compatibility;

import io.apicurio.registry.types.RegistryException;

/**
 * Thrown when a COMPATIBILITY rule is enforced against an artifact type that has no compatibility checker
 * implementation. Reporting such content as compatible would silently approve a breaking change, so the
 * operation is rejected instead.
 */
public class CompatibilityCheckNotSupportedException extends RegistryException {

    private static final long serialVersionUID = 1L;

    public CompatibilityCheckNotSupportedException(String message) {
        super(message);
    }

}
