package io.apicurio.registry.extensions;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response;

/**
 * Extension point for APIs that render errors in their own format (for example a protocol mandated by an
 * external specification). The core exception mappers ask each bean implementing this interface whether it
 * handles the current request, and use the first one that does; otherwise the core error format applies.
 */
public interface ApiExceptionMapper {

    /**
     * @param request the request being processed, possibly {@code null} outside an HTTP request
     * @return {@code true} if errors for this request must be mapped by this mapper
     */
    boolean handles(HttpServletRequest request);

    /**
     * Maps an exception thrown while processing a request this mapper {@link #handles handles}.
     *
     * @param t the exception
     * @return the error response
     */
    Response mapException(Throwable t);
}
