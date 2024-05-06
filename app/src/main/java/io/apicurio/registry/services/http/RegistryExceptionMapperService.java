package io.apicurio.registry.services.http;

import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.ccompat.rest.error.*;
import io.apicurio.registry.content.dereference.DereferencingNotSupportedException;
import io.apicurio.registry.limits.LimitExceededException;
import io.apicurio.registry.metrics.health.liveness.LivenessUtil;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.rest.MissingRequiredParameterException;
import io.apicurio.registry.rest.ParametersConflictException;
import io.apicurio.registry.rest.v3.beans.Error;
import io.apicurio.registry.rest.v3.beans.RuleViolationCause;
import io.apicurio.registry.rest.v3.beans.RuleViolationError;
import io.apicurio.registry.rules.DefaultRuleDeletionException;
import io.apicurio.registry.rules.RuleViolation;
import io.apicurio.registry.rules.RuleViolationException;
import io.apicurio.registry.rules.UnprocessableSchemaException;
import io.apicurio.registry.storage.error.*;
import io.apicurio.rest.client.auth.exception.ForbiddenException;
import io.apicurio.rest.client.auth.exception.NotAuthorizedException;
import io.smallrye.mutiny.TimeoutException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.ValidationException;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.stream.Collectors;

import static java.net.HttpURLConnection.*;

@ApplicationScoped
public class RegistryExceptionMapperService {

    private static final int HTTP_UNPROCESSABLE_ENTITY = 422;

    private static final Map<Class<? extends Exception>, Integer> CODE_MAP;

    @Inject
    Logger log;

    @Inject
    ResponseErrorLivenessCheck liveness;

    @Inject
    LivenessUtil livenessUtil;

    @ConfigProperty(name = "apicurio.api.errors.include-stack-in-response", defaultValue = "false")
    @Info(category = "api", description = "Include stack trace in errors responses", availableSince = "2.1.4.Final")
    boolean includeStackTrace;

    static {
        // TODO Merge this list with io.apicurio.registry.rest.RegistryExceptionMapper
        // Keep alphabetical

        Map<Class<? extends Exception>, Integer> map = new HashMap<>();
        map.put(AlreadyExistsException.class, HTTP_CONFLICT);
        map.put(ArtifactAlreadyExistsException.class, HTTP_CONFLICT);
        map.put(ArtifactNotFoundException.class, HTTP_NOT_FOUND);
        map.put(BadRequestException.class, HTTP_BAD_REQUEST);
        map.put(ArtifactBranchNotFoundException.class, HTTP_NOT_FOUND);
        map.put(ArtifactBranchAlreadyContainsVersionException.class, HTTP_CONFLICT);
        map.put(ConfigPropertyNotFoundException.class, HTTP_NOT_FOUND);
        map.put(ConflictException.class, HTTP_CONFLICT);
        map.put(ContentNotFoundException.class, HTTP_NOT_FOUND);
        map.put(DefaultRuleDeletionException.class, HTTP_CONFLICT);
        map.put(DownloadNotFoundException.class, HTTP_NOT_FOUND);
        map.put(ForbiddenException.class, HTTP_FORBIDDEN);
        map.put(GroupNotFoundException.class, HTTP_NOT_FOUND);
        map.put(InvalidArtifactIdException.class, HTTP_BAD_REQUEST);
        map.put(InvalidArtifactStateException.class, HTTP_BAD_REQUEST);
        map.put(InvalidVersionStateException.class, HTTP_BAD_REQUEST);
        map.put(InvalidArtifactTypeException.class, HTTP_BAD_REQUEST);
        map.put(InvalidGroupIdException.class, HTTP_BAD_REQUEST);
        map.put(InvalidPropertyValueException.class, HTTP_BAD_REQUEST);
        map.put(io.apicurio.registry.rest.ConflictException.class, HTTP_CONFLICT);
        map.put(LimitExceededException.class, HTTP_CONFLICT);
        map.put(LogConfigurationNotFoundException.class, HTTP_NOT_FOUND);
        map.put(MissingRequiredParameterException.class, HTTP_BAD_REQUEST);
        map.put(NotAllowedException.class, HTTP_CONFLICT); // We're using 409 instead of 403 to reserve the latter for authx only.
        map.put(NotAuthorizedException.class, HTTP_FORBIDDEN);
        map.put(NotFoundException.class, HTTP_NOT_FOUND);
        map.put(ParametersConflictException.class, HTTP_CONFLICT);
        map.put(ReadOnlyStorageException.class, HTTP_CONFLICT);
        map.put(ReferenceExistsException.class, HTTP_UNPROCESSABLE_ENTITY);
        map.put(DereferencingNotSupportedException.class, HTTP_BAD_REQUEST);
        map.put(RoleMappingAlreadyExistsException.class, HTTP_CONFLICT);
        map.put(RoleMappingNotFoundException.class, HTTP_NOT_FOUND);
        map.put(RuleAlreadyExistsException.class, HTTP_CONFLICT);
        map.put(RuleNotFoundException.class, HTTP_NOT_FOUND);
        map.put(RuleViolationException.class, HTTP_CONFLICT);
        map.put(SchemaNotFoundException.class, HTTP_NOT_FOUND);
        map.put(SchemaNotSoftDeletedException.class, HTTP_CONFLICT);
        map.put(SchemaSoftDeletedException.class, HTTP_CONFLICT);
        map.put(SubjectNotSoftDeletedException.class, HTTP_CONFLICT);
        map.put(SubjectSoftDeletedException.class, HTTP_NOT_FOUND);
        map.put(TimeoutException.class, HTTP_UNAVAILABLE);
        map.put(UnprocessableEntityException.class, HTTP_UNPROCESSABLE_ENTITY);
        map.put(UnprocessableSchemaException.class, HTTP_UNPROCESSABLE_ENTITY);
        map.put(ValidationException.class, HTTP_BAD_REQUEST);
        map.put(VersionAlreadyExistsException.class, HTTP_CONFLICT);
        map.put(VersionNotFoundException.class, HTTP_NOT_FOUND);

        CODE_MAP = Collections.unmodifiableMap(map);
    }

    public static Set<Class<? extends Exception>> getIgnored() {
        return CODE_MAP.keySet();
    }

    public ErrorHttpResponse mapException(Throwable t) {
        int code;
        Response response = null;
        if (t instanceof WebApplicationException) {
            WebApplicationException wae = (WebApplicationException) t;
            response = wae.getResponse();
            code = response.getStatus();
        } else {
            code = CODE_MAP.getOrDefault(t.getClass(), HTTP_INTERNAL_ERROR);
        }

        if (code == HTTP_INTERNAL_ERROR) {
            // If the error is not something we should ignore, then we report it to the liveness object
            // and log it.  Otherwise we only log it if debug logging is enabled.
            if (!livenessUtil.isIgnoreError(t)) {
                liveness.suspectWithException(t);
            }
            log.error("[500 ERROR DETECTED] : " + t.getMessage(), t);
        }

        Error error = toError(t, code);

        return new ErrorHttpResponse(code, error, response);

    }

    private Error toError(Throwable t, int code) {
        Error error;

        if (t instanceof RuleViolationException) {
            RuleViolationException rve = (RuleViolationException) t;
            error = new RuleViolationError();
            ((RuleViolationError) error).setCauses(toRestCauses(rve.getCauses()));
        } else {
            error = new Error();
        }

        error.setErrorCode(code);
        error.setMessage(t.getLocalizedMessage());
        if (includeStackTrace) {
            error.setDetail(getStackTrace(t));
        } else {
            error.setDetail(getRootMessage(t));
        }
        error.setName(t.getClass().getSimpleName());
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

    /**
     * Gets the full stack trace for the given exception and returns it as a
     * string.
     *
     * @param t
     */
    private static String getStackTrace(Throwable t) {
        try (StringWriter writer = new StringWriter()) {
            t.printStackTrace(new PrintWriter(writer));
            return writer.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static String getRootMessage(Throwable t) {
        return ExceptionUtils.getRootCauseMessage(t);
    }

}
