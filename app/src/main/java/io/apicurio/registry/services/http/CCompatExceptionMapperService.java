package io.apicurio.registry.services.http;

import io.apicurio.registry.ccompat.rest.error.ConflictException;
import io.apicurio.registry.ccompat.rest.error.ErrorCode;
import io.apicurio.registry.ccompat.rest.error.ReferenceExistsException;
import io.apicurio.registry.ccompat.rest.error.SchemaNotFoundException;
import io.apicurio.registry.ccompat.rest.error.SchemaNotSoftDeletedException;
import io.apicurio.registry.ccompat.rest.error.SchemaSoftDeletedException;
import io.apicurio.registry.ccompat.rest.error.SubjectNotSoftDeletedException;
import io.apicurio.registry.ccompat.rest.error.SubjectSoftDeletedException;
import io.apicurio.registry.ccompat.rest.error.UnprocessableEntityException;
import io.apicurio.registry.metrics.health.liveness.LivenessUtil;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.rest.v3.beans.Error;
import io.apicurio.registry.rest.v3.beans.RuleViolationCause;
import io.apicurio.registry.rest.v3.beans.RuleViolationError;
import io.apicurio.registry.rules.RuleViolation;
import io.apicurio.registry.rules.RuleViolationException;
import io.apicurio.registry.storage.error.AlreadyExistsException;
import io.apicurio.registry.storage.error.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.storage.error.ContentNotFoundException;
import io.apicurio.registry.storage.error.VersionNotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.net.HttpURLConnection.HTTP_CONFLICT;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;

@ApplicationScoped
public class CCompatExceptionMapperService {

    private static final Map<Class<? extends Exception>, Integer> CONFLUENT_CODE_MAP;

    static {
        Map<Class<? extends Exception>, Integer> map = new HashMap<>();
        map.put(AlreadyExistsException.class, HTTP_CONFLICT);
        map.put(ArtifactAlreadyExistsException.class, HTTP_CONFLICT);
        map.put(ArtifactNotFoundException.class, ErrorCode.SUBJECT_NOT_FOUND.value());
        map.put(ContentNotFoundException.class, ErrorCode.SCHEMA_NOT_FOUND.value());
        map.put(RuleViolationException.class, ErrorCode.INVALID_COMPATIBILITY_LEVEL.value());
        map.put(VersionNotFoundException.class, ErrorCode.VERSION_NOT_FOUND.value());
        map.put(UnprocessableEntityException.class, ErrorCode.INVALID_SCHEMA.value());
        map.put(ConflictException.class, HTTP_CONFLICT);
        map.put(SubjectNotSoftDeletedException.class, ErrorCode.SUBJECT_NOT_SOFT_DELETED.value());
        map.put(SchemaNotSoftDeletedException.class, ErrorCode.SCHEMA_VERSION_NOT_SOFT_DELETED.value());
        map.put(SchemaSoftDeletedException.class, ErrorCode.SCHEMA_VERSION_SOFT_DELETED.value());
        map.put(SubjectSoftDeletedException.class, ErrorCode.SUBJECT_SOFT_DELETED.value());
        map.put(ReferenceExistsException.class, ErrorCode.REFERENCE_EXISTS.value());
        map.put(SchemaNotFoundException.class, ErrorCode.SCHEMA_NOT_FOUND.value());
        CONFLUENT_CODE_MAP = Collections.unmodifiableMap(map);
    }

    @Inject
    Logger log;

    @Inject
    ResponseErrorLivenessCheck liveness;

    @Inject
    LivenessUtil livenessUtil;

    public ErrorHttpResponse mapException(Throwable t) {
        int code = 0;
        Response response = null;
        if (t instanceof WebApplicationException) {
            WebApplicationException wae = (WebApplicationException) t;
            response = wae.getResponse();
            code = response.getStatus();
        } else {
            code = CoreV2RegistryExceptionMapperService.CODE_MAP.getOrDefault(t.getClass(), HTTP_INTERNAL_ERROR);
        }

        if (code == HTTP_INTERNAL_ERROR) {
            // If the error is not something we should ignore, then we report it to the liveness object
            // and log it. Otherwise we only log it if debug logging is enabled.
            if (!livenessUtil.isIgnoreError(t)) {
                liveness.suspectWithException(t);
            }
            log.error("[500 ERROR DETECTED] : " + t.getMessage(), t);
        }

        Error error = toError(t);

        return ErrorHttpResponse.builder()
                .status(error.getErrorCode())
                .jaxrsResponse(response)
                .error(error)
                .contentType(MediaType.APPLICATION_JSON)
                .build();
    }

    private Error toError(Throwable t) {
        Error error;

        if (t instanceof RuleViolationException) {
            RuleViolationException rve = (RuleViolationException) t;
            error = new RuleViolationError();
            ((RuleViolationError) error).setCauses(toRestCauses(rve.getCauses()));
        } else {
            error = new Error();
        }

        error.setErrorCode(CONFLUENT_CODE_MAP.getOrDefault(t.getClass(), 0));
        error.setMessage(t.getLocalizedMessage());
        return error;
    }

    /**
     * Converts rule violations to appropriate error beans.
     *
     * @param violations
     */
    private List<RuleViolationCause> toRestCauses(Set<RuleViolation> violations) {
        if (violations == null) {
            return null;
        }
        return violations.stream().map(violation -> {
            RuleViolationCause cause = new RuleViolationCause();
            cause.setContext(violation.getContext());
            cause.setDescription(violation.getDescription());
            return cause;
        }).collect(Collectors.toList());
    }

}
