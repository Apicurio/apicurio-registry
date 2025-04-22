package io.apicurio.registry.script;

import io.apicurio.registry.types.RegistryException;

public class ScriptExecutionException extends RegistryException {

    private static final long serialVersionUID = 0;

    protected ScriptExecutionException(Throwable cause) {
        super(cause);
    }

    protected ScriptExecutionException(String reason, Throwable cause) {
        super(reason, cause);
    }

    protected ScriptExecutionException(String reason) {
        super(reason);
    }

}
