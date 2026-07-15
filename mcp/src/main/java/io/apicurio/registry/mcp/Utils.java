package io.apicurio.registry.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.apicurio.registry.rest.client.models.Labels;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import io.apicurio.registry.rest.client.models.RuleViolationProblemDetails;
import io.quarkiverse.mcp.server.ToolCallException;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

@ApplicationScoped
public class Utils {

    @Inject
    ObjectMapper mapper;

    public String toPrettyJson(Object value) {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, String> toRawLabels(String jsonMap) {
        try {
            var rawLabels = new HashMap<String, String>();
            var node = mapper.readTree(jsonMap);
            if (node.isObject()) {
                var objectNode = (ObjectNode) node;
                for (Iterator<Entry<String, JsonNode>> it = objectNode.fields(); it.hasNext(); ) {
                    var f = it.next();
                    if (f.getValue().isTextual()) {
                        rawLabels.put(f.getKey(), f.getValue().textValue());
                    } else {
                        throw new RuntimeException("Value for field \"" + f.getKey() + "\" is not a string.");
                    }
                }
            } else {
                throw new RuntimeException("Provided labels object is not a JSON map.");
            }
            return rawLabels;
        } catch (Exception ex) {
            throw new ToolCallException("Error: Labels must be represented as a JSON map, with string keys and string values. Details: " + ex.getMessage());
        }
    }


    public Labels toLabels(String jsonMap) {
        if (jsonMap == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        var rawLabels = (Map<String, Object>) (Map<?, ?>) toRawLabels(jsonMap);
        var labels = new Labels();
        labels.setAdditionalData(rawLabels);
        return labels;
    }

    public String[] toQueryLabels(String jsonMap) {
        if (jsonMap == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        var rawLabels = (Map<String, Object>) (Map<?, ?>) toRawLabels(jsonMap);
        return (String[]) rawLabels.entrySet().stream()
                .map(e -> e.getKey() + ":" + e.getValue())
                .toArray();
    }

    public static <R> R handleError(Function0R<R> action) {
        try {
            return action.apply();
        } catch (ProblemDetails ex) {
            throw new ToolCallException(formatRegistryApiError(ex), ex);
        } catch (RuleViolationProblemDetails ex) {
            throw new ToolCallException(formatRegistryApiError(ex), ex);
        } catch (Exception ex) {
            Throwable registryError = findRegistryApiError(ex);
            if (registryError instanceof RuleViolationProblemDetails ruleViolation) {
                throw new ToolCallException(formatRegistryApiError(ruleViolation), ex);
            }
            if (registryError instanceof ProblemDetails problem) {
                throw new ToolCallException(formatRegistryApiError(problem), ex);
            }
            if (ex.getMessage() == null) {
                throw new ToolCallException(
                        "Request to Apicurio Registry failed with an unknown error. Ask the user to check Apicurio Registry server logs.");
            }
            throw new ToolCallException("Execution failed: " + ex.getMessage(), ex);
        }
    }

    static String formatRegistryApiError(ProblemDetails ex) {
        return formatRegistryApiError(ex, includeAuthorizationHints());
    }

    static String formatRegistryApiError(ProblemDetails ex, boolean includeAuthorizationHints) {
        return formatRegistryApiError(ex.getStatus(), ex.getDetail(), ex.getTitle(), ex.getName(),
                includeAuthorizationHints);
    }

    static String formatRegistryApiError(RuleViolationProblemDetails ex) {
        return formatRegistryApiError(ex, includeAuthorizationHints());
    }

    static String formatRegistryApiError(RuleViolationProblemDetails ex, boolean includeAuthorizationHints) {
        StringBuilder message = new StringBuilder(
                formatRegistryApiError(ex.getStatus(), ex.getDetail(), ex.getTitle(), ex.getName(),
                        includeAuthorizationHints));
        if (ex.getCauses() != null && !ex.getCauses().isEmpty()) {
            String causes = ex.getCauses().stream()
                    .map(cause -> firstNonBlank(cause.getContext(), "rule") + ": "
                            + firstNonBlank(cause.getDescription(), "violation"))
                    .collect(Collectors.joining("; "));
            message.append(" Causes: ").append(causes);
        }
        return message.toString();
    }

    private static String formatRegistryApiError(Integer status, String detail, String title, String name,
            boolean includeAuthorizationHints) {
        StringBuilder message = new StringBuilder("Request to Apicurio Registry returned an error");
        if (status != null) {
            message.append(" (HTTP ").append(status);
            if (status == 403) {
                message.append(" Forbidden");
            } else if (status == 401) {
                message.append(" Unauthorized");
            }
            message.append(')');
        }
        message.append(": ").append(firstNonBlank(detail, title, name, "Unknown error"));
        // Only include Registry role names for authenticated callers — avoids leaking the
        // authorization model to unauthenticated probes that receive 401/403.
        if (includeAuthorizationHints && Integer.valueOf(403).equals(status)) {
            message.append(". Your Keycloak user may be missing the required Registry role "
                    + "(sr-readonly, sr-developer, or sr-admin for this operation).");
        }
        return message.toString();
    }

    /**
     * Role-name hints are only useful (and safe) when the caller is already authenticated.
     */
    static boolean includeAuthorizationHints() {
        try {
            Instance<SecurityIdentity> identity = CDI.current().select(SecurityIdentity.class);
            if (!identity.isResolvable()) {
                return false;
            }
            SecurityIdentity securityIdentity = identity.get();
            return securityIdentity != null && !securityIdentity.isAnonymous();
        } catch (IllegalStateException ignored) {
            // Outside a CDI context (unit tests, stdio without security)
            return false;
        }
    }

    private static Throwable findRegistryApiError(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof ProblemDetails || current instanceof RuleViolationProblemDetails) {
                return current;
            }
            current = current.getCause();
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    public static String handleError(Function0 action) {
        return handleError(() -> {
            action.apply();
            return "Success";
        });
    }

    @FunctionalInterface
    public interface Function0R<R> {

        R apply() throws Exception;
    }

    @FunctionalInterface
    public interface Function0 {

        void apply() throws Exception;
    }
}
