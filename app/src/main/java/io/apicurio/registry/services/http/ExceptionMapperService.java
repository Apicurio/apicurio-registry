package io.apicurio.registry.services.http;

import jakarta.ws.rs.core.Response;

public interface ExceptionMapperService {

    Response mapException(Throwable t);
}
