package io.apicurio.registry.asyncapi.rules.validity;

import io.apicurio.registry.openapi.rules.validity.ApicurioDataModelContentValidator;

/**
 * A content validator implementation for the AsyncAPI content type.
 */
public class AsyncApiContentValidator extends ApicurioDataModelContentValidator {

    /**
     * @see ApicurioDataModelContentValidator#getDataModelType()
     */
    @Override
    protected String getDataModelType() {
        return "AsyncAPI";
    }

}
