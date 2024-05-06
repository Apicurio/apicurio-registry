package io.apicurio.registry.content.dereference;

import io.apicurio.registry.types.RegistryException;

public class DereferencingNotSupportedException extends RegistryException {

    private static final long serialVersionUID = 3870450993208569387L;

    public DereferencingNotSupportedException() {
    }

    public DereferencingNotSupportedException(String reason) {
        super(reason);
    }

    public DereferencingNotSupportedException(Throwable cause) {
        super(cause);
    }

    public DereferencingNotSupportedException(String reason, Throwable cause) {
        super(reason, cause);
    }
}
