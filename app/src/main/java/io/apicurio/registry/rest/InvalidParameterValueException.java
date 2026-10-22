package io.apicurio.registry.rest;

import io.apicurio.registry.types.RegistryException;

public class InvalidParameterValueException extends RegistryException {

    private static final long serialVersionUID = 1042121903168890335L;

    /**
     * Constructor.
     */
    public InvalidParameterValueException(String parameter, String expectedValue, String actualValue) {
        super("Request contains parameter '" + parameter + "' with invalid value.  Expected '" + expectedValue + "' but got '" + actualValue + "'");
    }

}
