package io.apicurio.registry.rules.validity;

/**
 * A content validator implementation for the OpenAPI content type.
 */
public class OpenApiContentValidator extends ApicurioDataModelContentValidator {

    /**
     * @see io.apicurio.registry.rules.validity.ApicurioDataModelContentValidator#getDataModelType()
     */
    @Override
    protected String getDataModelType() {
        return "OpenAPI";
    }

}
