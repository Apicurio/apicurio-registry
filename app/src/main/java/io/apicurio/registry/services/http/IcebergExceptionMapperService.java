package io.apicurio.registry.services.http;

import io.apicurio.registry.iceberg.metrics.IcebergMetricsService;
import io.apicurio.registry.iceberg.rest.v1.beans.ErrorModel;
import io.apicurio.registry.iceberg.rest.v1.beans.IcebergErrorResponse;
import io.apicurio.registry.storage.error.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.storage.error.CommitFailedException;
import io.apicurio.registry.storage.error.GroupAlreadyExistsException;
import io.apicurio.registry.storage.error.GroupNotEmptyException;
import io.apicurio.registry.storage.error.GroupNotFoundException;
import io.apicurio.registry.storage.error.VersionAlreadyExistsException;
import io.apicurio.registry.storage.error.VersionNotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Exception mapper service for Iceberg REST API errors.
 * Maps storage exceptions to Iceberg-formatted error responses.
 */
@ApplicationScoped
public class IcebergExceptionMapperService {

    private static final String CONTENT_HEADER_PREFIX = "Content-";

    @Inject
    IcebergMetricsService metricsService;

    public Response mapException(Throwable t) {
        if (t instanceof NotFoundException) {
            return buildErrorResponse(404, "NotFoundException",
                    t.getMessage() != null ? t.getMessage() : "Not found");
        }

        if (t instanceof GroupNotFoundException) {
            return buildErrorResponse(404, "NoSuchNamespaceException",
                    "Namespace does not exist: " + ((GroupNotFoundException) t).getGroupId());
        }

        if (t instanceof ArtifactNotFoundException) {
            return buildErrorResponse(404, "NoSuchTableException",
                    "Table does not exist: " + ((ArtifactNotFoundException) t).getArtifactId());
        }

        if (t instanceof VersionNotFoundException) {
            return buildErrorResponse(404, "NoSuchTableException",
                    "Table version does not exist");
        }

        if (t instanceof GroupAlreadyExistsException) {
            return buildErrorResponse(409, "AlreadyExistsException",
                    "Namespace already exists: " + ((GroupAlreadyExistsException) t).getGroupId());
        }

        if (t instanceof ArtifactAlreadyExistsException) {
            return buildErrorResponse(409, "AlreadyExistsException",
                    "Table already exists: " + ((ArtifactAlreadyExistsException) t).getArtifactId());
        }

        if (t instanceof GroupNotEmptyException) {
            return buildErrorResponse(409, "NamespaceNotEmptyException",
                    "Namespace is not empty: " + ((GroupNotEmptyException) t).getGroupId());
        }

        if (t instanceof CommitFailedException) {
            metricsService.recordCommitConflict("table");
            return buildErrorResponse(409, "CommitFailedException", t.getMessage());
        }

        if (t instanceof VersionAlreadyExistsException) {
            metricsService.recordCommitConflict("table");
            return buildErrorResponse(409, "CommitFailedException",
                    "Version already exists: " + ((VersionAlreadyExistsException) t).getVersion());
        }

        if (t instanceof IllegalArgumentException) {
            return buildErrorResponse(400, "BadRequestException", t.getMessage());
        }

        // Keep a deliberate status rather than falling through to 500.
        if (t instanceof WebApplicationException wae) {
            return mapWebApplicationException(wae);
        }

        // Default to internal server error
        return buildErrorResponse(500, "InternalServerError",
                t.getMessage() != null ? t.getMessage() : "An unexpected error occurred");
    }

    private Response mapWebApplicationException(WebApplicationException wae) {
        Response original = wae.getResponse();
        int status = original.getStatus();
        if (status < 400) {
            // A redirect is not an error: keep it as thrown, with its Location header.
            return original;
        }
        Response.ResponseBuilder builder = errorResponseBuilder(status, errorType(wae, status),
                wae.getMessage());
        // Forward protocol headers such as Allow (405) and Retry-After (503). The body is replaced,
        // so headers that describe the original one are not carried over.
        original.getHeaders().forEach((name, values) -> {
            if (!describesBody(name)) {
                values.forEach(value -> builder.header(name, value));
            }
        });
        return builder.build();
    }

    /**
     * The error {@code type} for an exception that carries its own status. A named subclass such as
     * {@code ServiceUnavailableException} keeps its name. A plain or anonymous
     * {@code WebApplicationException} has no useful name, so the type is derived from the status.
     */
    private static String errorType(WebApplicationException wae, int status) {
        Class<?> exceptionClass = wae.getClass();
        if (exceptionClass != WebApplicationException.class && !exceptionClass.isAnonymousClass()) {
            return exceptionClass.getSimpleName();
        }
        if (status >= 500) {
            return "InternalServerError";
        }
        Response.Status known = Response.Status.fromStatusCode(status);
        return known != null
                ? known.getReasonPhrase().replaceAll("[^A-Za-z0-9]", "") + "Exception"
                : "ClientErrorException";
    }

    private static boolean describesBody(String header) {
        return header.regionMatches(true, 0, CONTENT_HEADER_PREFIX, 0, CONTENT_HEADER_PREFIX.length())
                || HttpHeaders.ETAG.equalsIgnoreCase(header)
                || HttpHeaders.LAST_MODIFIED.equalsIgnoreCase(header);
    }

    private Response buildErrorResponse(int code, String type, String message) {
        return errorResponseBuilder(code, type, message).build();
    }

    private Response.ResponseBuilder errorResponseBuilder(int code, String type, String message) {
        metricsService.recordIcebergError(type);

        ErrorModel error = new ErrorModel();
        error.setCode(code);
        error.setType(type);
        error.setMessage(message);

        IcebergErrorResponse response = new IcebergErrorResponse();
        response.setError(error);

        return Response.status(code)
                .type(MediaType.APPLICATION_JSON)
                .entity(response);
    }
}
