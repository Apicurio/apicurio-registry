package io.apicurio.registry.iceberg.rest.v1;

import io.apicurio.registry.iceberg.rest.v1.beans.ErrorModel;
import io.apicurio.registry.iceberg.rest.v1.beans.IcebergErrorResponse;
import io.apicurio.registry.storage.error.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.storage.error.GroupAlreadyExistsException;
import io.apicurio.registry.storage.error.GroupNotEmptyException;
import io.apicurio.registry.storage.error.GroupNotFoundException;
import io.apicurio.registry.storage.error.VersionNotFoundException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Exception mapper for Iceberg REST API errors.
 * Maps storage exceptions to Iceberg-formatted error responses.
 */
@Provider
public class IcebergExceptionMapper implements ExceptionMapper<Exception> {

    private static final String ICEBERG_PATH_PREFIX = "/apis/iceberg/";

    @Override
    public Response toResponse(Exception exception) {
        // Only handle exceptions for Iceberg API paths
        // Other paths should be handled by their own mappers

        if (exception instanceof GroupNotFoundException) {
            return buildErrorResponse(404, "NoSuchNamespaceException",
                    "Namespace does not exist: " + ((GroupNotFoundException) exception).getGroupId());
        }

        if (exception instanceof ArtifactNotFoundException) {
            return buildErrorResponse(404, "NoSuchTableException",
                    "Table does not exist: " + ((ArtifactNotFoundException) exception).getArtifactId());
        }

        if (exception instanceof VersionNotFoundException) {
            return buildErrorResponse(404, "NoSuchTableException",
                    "Table version does not exist");
        }

        if (exception instanceof GroupAlreadyExistsException) {
            return buildErrorResponse(409, "AlreadyExistsException",
                    "Namespace already exists: " + ((GroupAlreadyExistsException) exception).getGroupId());
        }

        if (exception instanceof ArtifactAlreadyExistsException) {
            return buildErrorResponse(409, "AlreadyExistsException",
                    "Table already exists: " + ((ArtifactAlreadyExistsException) exception).getArtifactId());
        }

        if (exception instanceof GroupNotEmptyException) {
            return buildErrorResponse(409, "NamespaceNotEmptyException",
                    "Namespace is not empty: " + ((GroupNotEmptyException) exception).getGroupId());
        }

        if (exception instanceof IllegalArgumentException) {
            return buildErrorResponse(400, "BadRequestException", exception.getMessage());
        }

        // Default to internal server error
        return buildErrorResponse(500, "InternalServerError",
                exception.getMessage() != null ? exception.getMessage() : "An unexpected error occurred");
    }

    private Response buildErrorResponse(int code, String type, String message) {
        ErrorModel error = new ErrorModel();
        error.setCode(code);
        error.setType(type);
        error.setMessage(message);

        IcebergErrorResponse response = new IcebergErrorResponse();
        response.setError(error);

        return Response.status(code)
                .type(MediaType.APPLICATION_JSON)
                .entity(response)
                .build();
    }
}
