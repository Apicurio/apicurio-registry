package io.apicurio.registry.services.http;

import io.apicurio.registry.mcpregistry.rest.v0.beans.Error;
import io.apicurio.registry.metrics.health.liveness.LivenessUtil;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;

import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;

/**
 * Maps exceptions raised on the MCP Registry API to the error body its specification defines,
 * <code>{"error": "..."}</code>, instead of the v3 ProblemDetails shape, which also names the Java exception
 * class. Status codes come from the same {@link HttpStatusCodeMap} the core mapper uses, so only the body
 * differs between the two.
 */
@ApplicationScoped
public class McpRegistryExceptionMapperService {

    private static final String PATH_PREFIX = "/apis/mcp-registry";

    @Inject
    Logger log;

    @Inject
    ResponseErrorLivenessCheck liveness;

    @Inject
    LivenessUtil livenessUtil;

    @Inject
    HttpStatusCodeMap codeMap;

    /**
     * True for requests to the MCP Registry API, whose errors this service maps.
     */
    public static boolean handles(HttpServletRequest request) {
        return request != null && request.getRequestURI().contains(PATH_PREFIX);
    }

    public Response mapException(Throwable t) {
        Response response = null;
        int code;
        if (t instanceof WebApplicationException wae) {
            response = wae.getResponse();
            code = response.getStatus();
        } else {
            code = codeMap.getCode(t.getClass());
        }

        if (code == HTTP_INTERNAL_ERROR) {
            if (!livenessUtil.isIgnoreError(t)) {
                liveness.suspectWithException(t);
            }
            log.error("[500 ERROR DETECTED] : " + t.getMessage(), t);
        }

        Error error = new Error();
        error.setError(message(t, code));

        // Built from the original response when there is one, so headers such as a 405's Allow survive.
        Response.ResponseBuilder builder = response != null
                ? Response.fromResponse(response)
                : Response.status(code);
        return builder.entity(error).type(MediaType.APPLICATION_JSON).build();
    }

    /**
     * An unexpected server error - a 500, or a storage exception mapped to another 5xx - gets the reason
     * phrase only, since its message is internal. So does a framework message carrying a RESTEasy diagnostic
     * code, such as the one for an unsupported method. A 5xx raised on purpose as a WebApplicationException,
     * such as the 501 for a read-only registry, keeps its message.
     */
    private static String message(Throwable t, int code) {
        String message = t.getMessage();
        boolean internal = code == HTTP_INTERNAL_ERROR
                || (code > HTTP_INTERNAL_ERROR && !(t instanceof WebApplicationException));
        if (internal || message == null || message.isBlank() || message.startsWith("RESTEASY")) {
            Response.Status status = Response.Status.fromStatusCode(code);
            return status != null ? status.getReasonPhrase() : "Request failed";
        }
        return message;
    }
}
