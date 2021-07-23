package io.apicurio.registry.rest;

import io.apicurio.registry.types.RegistryException;

import java.util.Arrays;

public class ParametersConflictException extends RegistryException {

    private final String[] parameters;

    public ParametersConflictException(String parameter1, String parameter2) {
        super("Conflict: '" + parameter1 + "' and '" + parameter2 + "' are mutually exclusive.");
        this.parameters = new String[] {parameter1, parameter2};
    }

    public ParametersConflictException(String... parameters) {
        super("Conflict: [" + String.join(",", parameters) + "] are mutually exclusive.");
        this.parameters = Arrays.stream(parameters).toArray(String[]::new);
    }

    public String[] getParameters() {
        return parameters;
    }
}
