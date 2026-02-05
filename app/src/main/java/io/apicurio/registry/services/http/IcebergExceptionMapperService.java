package io.apicurio.registry.services.http;

import io.apicurio.registry.iceberg.rest.v1.beans.ErrorModel;
import io.apicurio.registry.iceberg.rest.v1.beans.IcebergErrorResponse;
import io.apicurio.registry.storage.error.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.storage.error.GroupAlreadyExistsException;
import io.apicurio.registry.storage.error.GroupNotEmptyException;
import io.apicurio.registry.storage.error.GroupNotFoundException;
import io.apicurio.registry.storage.error.VersionNotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Exception mapper service for Iceberg REST API errors.
 * Maps storage exceptions to Iceberg-formatted error responses.
 */
@ApplicationScoped
public class IcebergExceptionMapperService {

    public Response mapException(Throwable t) {
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

        if (t instanceof IllegalArgumentException) {
            return buildErrorResponse(400, "BadRequestException", t.getMessage());
        }

        // Default to internal server error
        return buildErrorResponse(500, "InternalServerError",
                t.getMessage() != null ? t.getMessage() : "An unexpected error occurred");
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
