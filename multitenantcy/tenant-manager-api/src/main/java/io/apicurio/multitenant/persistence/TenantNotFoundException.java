package io.apicurio.multitenant.persistence;

public class TenantNotFoundException extends RuntimeException {

    /**
     *
     */
    private static final long serialVersionUID = 3830931257679125603L;

    public TenantNotFoundException() {
        super();
    }

    public TenantNotFoundException(String message) {
        super(message);
    }

    public static TenantNotFoundException create(String tenantId) {
        return new TenantNotFoundException("No tenant found for tenantId " + tenantId);
    }

}
