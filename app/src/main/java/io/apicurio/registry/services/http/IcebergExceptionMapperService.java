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
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.apicurio.common.apps.config.Info;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_API;

/**
 * Exception mapper service for Iceberg REST API errors.
 * Maps storage exceptions to Iceberg-formatted error responses.
 */
@ApplicationScoped
public class IcebergExceptionMapperService {

    @Inject
    IcebergMetricsService metricsService;

    @ConfigProperty(name = "apicurio.api.errors.include-stack-in-response", defaultValue = "false")
    @Info(category = CATEGORY_API, description = "Include stack trace in errors responses", availableSince = "2.1.4.Final")
    boolean includeStackTrace;

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

        // Default to internal server error
        String message = (includeStackTrace && t.getMessage() != null) ? t.getMessage() : "An unexpected error occurred";
        return buildErrorResponse(500, "InternalServerError", message);
    }

    private Response buildErrorResponse(int code, String type, String message) {
        metricsService.recordIcebergError(type);

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
