package io.apicurio.registry.rest;

import io.apicurio.registry.types.RegistryException;

public class MissingRequiredParameterException extends RegistryException {

    private static final long serialVersionUID = 3318387244830092754L;

    private final String parameter;

    /**
     * Constructor.
     */
    public MissingRequiredParameterException(String parameter) {
        super("Request is missing a required parameter: " + parameter);
        this.parameter = parameter;
    }

    /**
     * @return the parameter
     */
    public String getParameter() {
        return parameter;
    }

}
