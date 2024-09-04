package io.apicurio.registry.services.http;

import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.metrics.health.liveness.LivenessUtil;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.rest.v3.beans.Error;
import io.apicurio.registry.rest.v3.beans.RuleViolationCause;
import io.apicurio.registry.rest.v3.beans.RuleViolationError;
import io.apicurio.registry.rules.RuleViolation;
import io.apicurio.registry.rules.RuleViolationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;

/**
 * @deprecated This exception mapper logic is used for legacy v2 API support only.
 */
@ApplicationScoped
public class CoreV2RegistryExceptionMapperService {

    @Inject
    Logger log;

    @Inject
    ResponseErrorLivenessCheck liveness;

    @Inject
    LivenessUtil livenessUtil;

    @Inject
    HttpStatusCodeMap codeMap;

    @ConfigProperty(name = "apicurio.api.errors.include-stack-in-response", defaultValue = "false")
    @Info(category = "api", description = "Include stack trace in errors responses", availableSince = "2.1.4.Final")
    boolean includeStackTrace;

    public Response mapException(Throwable t) {
        int code;
        Response response = null;
        if (t instanceof WebApplicationException) {
            WebApplicationException wae = (WebApplicationException) t;
            response = wae.getResponse();
            code = response.getStatus();
        } else {
            code = codeMap.getCode(t.getClass());
        }

        if (code == HTTP_INTERNAL_ERROR) {
            // If the error is not something we should ignore, then we report it to the liveness object
            // and log it. Otherwise we only log it if debug logging is enabled.
            if (!livenessUtil.isIgnoreError(t)) {
                liveness.suspectWithException(t);
            }
            log.error("[500 ERROR DETECTED] : " + t.getMessage(), t);
        }

        Response.ResponseBuilder builder;
        if (response != null) {
            builder = Response.fromResponse(response);
        } else {
            builder = Response.status(code);
        }

        Error error = toError(t, code);
        return builder.entity(error).type(MediaType.APPLICATION_JSON).build();
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
     * Gets the full stack trace for the given exception and returns it as a string.
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
