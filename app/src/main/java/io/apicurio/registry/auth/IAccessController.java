package io.apicurio.registry.auth;

import jakarta.interceptor.InvocationContext;

public interface IAccessController {

    public boolean isAuthorized(InvocationContext context);

}
