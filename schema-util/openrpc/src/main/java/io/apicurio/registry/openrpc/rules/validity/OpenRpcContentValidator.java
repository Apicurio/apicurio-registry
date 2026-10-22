package io.apicurio.registry.openrpc.rules.validity;

import io.apicurio.registry.openapi.rules.validity.ApicurioDataModelContentValidator;

/**
 * A content validator implementation for the OpenRPC content type.
 */
public class OpenRpcContentValidator extends ApicurioDataModelContentValidator {

    /**
     * @see ApicurioDataModelContentValidator#getDataModelType()
     */
    @Override
    protected String getDataModelType() {
        return "OpenRPC";
    }

}
