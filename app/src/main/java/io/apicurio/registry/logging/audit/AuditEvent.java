package io.apicurio.registry.logging.audit;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.List;
import java.util.Locale;

/**
 * One audit event per audited HTTP request. Field names and meanings follow the {@code audit.k8s.io/v1}
 * {@code Event} where one exists. The event is recorded at the Kubernetes {@code Metadata} level: it has
 * no field for a request or response body, a header value other than {@code X-Forwarded-For} and
 * {@code User-Agent}, an exception message or a stack trace. Every component is a string, a list of
 * strings, an int, an enum or one of the nested records below, so a caller cannot attach an arbitrary
 * object or map to an event.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditEvent(
        String apiVersion,
        String kind,
        String level,
        String auditId,
        String traceId,
        String stage,
        String requestReceivedTimestamp,
        String stageTimestamp,
        String verb,
        String requestURI,
        String api,
        String operation,
        User user,
        List<String> sourceIPs,
        String forwardedFor,
        String userAgent,
        ObjectRef objectRef,
        ResponseStatus responseStatus,
        Outcome outcome) {

    public static final String API_VERSION = "audit.registry.apicur.io/v1";
    public static final String KIND = "Event";
    public static final String LEVEL_METADATA = "Metadata";
    public static final String STAGE_RESPONSE_COMPLETE = "ResponseComplete";
    public static final String ANONYMOUS_USERNAME = "system:anonymous";

    /**
     * An event with the fixed {@code apiVersion}, {@code kind}, {@code level} and {@code stage}.
     */
    public AuditEvent(String auditId, String traceId, String requestReceivedTimestamp, String stageTimestamp,
            String verb, String requestURI, String api, String operation, User user, List<String> sourceIPs,
            String forwardedFor, String userAgent, ObjectRef objectRef, ResponseStatus responseStatus,
            Outcome outcome) {
        this(API_VERSION, KIND, LEVEL_METADATA, auditId, traceId, STAGE_RESPONSE_COMPLETE,
                requestReceivedTimestamp, stageTimestamp, verb, requestURI, api, operation, user, sourceIPs,
                forwardedFor, userAgent, objectRef, responseStatus, outcome);
    }

    /**
     * Who made the request: the authenticated principal name and its roles, sorted.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record User(String username, List<String> groups) {
    }

    /**
     * What the request addressed, taken from the path parameters of the matched endpoint and, for
     * successful creates, from the identifiers in the response. {@code resource} is the last fixed segment
     * of the endpoint path (for example {@code artifacts}, {@code rules}, {@code mode}); {@code name} is
     * the identifier of the addressed object when it is not a group, artifact or version (a rule type, a
     * ccompat subject, a property name, an Iceberg table, an MCP server name and so on).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ObjectRef(String resource, String groupId, String artifactId, String version,
            String namespace, String name) {
    }

    /**
     * The final HTTP status. {@code reason} is the standard reason for the status code (for example
     * {@code NotFound}), never an exception message; it is absent for successful responses.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ResponseStatus(int code, String reason) {
    }

    public enum Outcome {
        SUCCESS, DENIED, FAILURE;

        @JsonValue
        public String jsonValue() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Outcome of(int status) {
            if (AuditedOperations.isDenied(status)) {
                return DENIED;
            }
            return status < 400 ? SUCCESS : FAILURE;
        }
    }
}
