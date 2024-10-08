package io.apicurio.registry.rest.v2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Context;

public abstract class AbstractResourceImpl {

    @Context
    HttpServletRequest request;
}
