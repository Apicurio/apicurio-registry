package io.apicurio.registry.services.http;

import io.apicurio.registry.metrics.health.liveness.LivenessUtil;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.rest.v3.beans.ProblemDetails;
import io.apicurio.registry.rest.v3.beans.RuleViolationCause;
import io.apicurio.registry.rest.v3.beans.RuleViolationProblemDetails;
import io.apicurio.registry.rules.violation.RuleViolation;
import io.apicurio.registry.rules.violation.RuleViolationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;

@ApplicationScoped
public class CoreRegistryExceptionMapperService {

    private static final String INTERNAL_SERVER_ERROR_TITLE = "Internal server error";
    private static final String INTERNAL_SERVER_ERROR_DETAIL = "An unexpected server error occurred.";
    private static final String UNKNOWN_ERROR_DETAIL = "The request could not be completed.";

    @Inject
    Logger log;

    @Inject
    ResponseErrorLivenessCheck liveness;

    @Inject
    LivenessUtil livenessUtil;

    @Inject
    HttpStatusCodeMap codeMap;

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

        ProblemDetails error = toProblemDetails(t, code);
        return builder.entity(error).type(MediaType.APPLICATION_JSON).build();
    }

    private ProblemDetails toProblemDetails(Throwable t, int code) {
        ProblemDetails details;

        if (t instanceof RuleViolationException) {
            RuleViolationException rve = (RuleViolationException) t;
            details = new RuleViolationProblemDetails();
            ((RuleViolationProblemDetails) details).setTitle(rve.getMessage());
            ((RuleViolationProblemDetails) details).setDetail(rve.getDetailMessage());
            ((RuleViolationProblemDetails) details).setCauses(toRestCauses(rve.getCauses()));
        } else {
            details = new ProblemDetails();
            if (code == HTTP_INTERNAL_ERROR) {
                details.setTitle(INTERNAL_SERVER_ERROR_TITLE);
                details.setDetail(INTERNAL_SERVER_ERROR_DETAIL);
            } else {
                String message = getPublicMessage(t);
                details.setTitle(message);
                details.setDetail(message);
            }
        }

        details.setStatus(code);
        details.setName(codeMap.getErrorCode(t.getClass(), code).name());
        return details;
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

    private static String getPublicMessage(Throwable t) {
        String message = t.getLocalizedMessage();
        return message != null && !message.isBlank() ? message : UNKNOWN_ERROR_DETAIL;
    }

}
