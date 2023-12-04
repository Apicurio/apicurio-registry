package io.apicurio.registry.rules.validity;

/**
 * A content validator implementation for the AsyncAPI content type.
 */
public class AsyncApiContentValidator extends ApicurioDataModelContentValidator {

    /**
     * @see io.apicurio.registry.rules.validity.ApicurioDataModelContentValidator#getDataModelType()
     */
    @Override
    protected String getDataModelType() {
        return "AsyncAPI";
    }

}
