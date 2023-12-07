package io.apicurio.registry.rest;

import java.util.Arrays;

public class ParametersConflictException extends ConflictException {

    private static final long serialVersionUID = 247427865185425744L;

    private final String[] parameters;

    public ParametersConflictException(String parameter1, String parameter2) {
        super("Conflict: '" + parameter1 + "' and '" + parameter2 + "' are mutually exclusive.");
        this.parameters = new String[]{parameter1, parameter2};
    }

    public ParametersConflictException(String... parameters) {
        super("Conflict: [" + String.join(",", parameters) + "] are mutually exclusive.");
        this.parameters = Arrays.stream(parameters).toArray(String[]::new);
    }

    public String[] getParameters() {
        return parameters;
    }
}
