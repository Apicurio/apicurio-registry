package io.apicurio.registry.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.apicurio.registry.rest.client.models.Labels;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import io.quarkiverse.mcp.server.ToolCallException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

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
            throw new ToolCallException("Request to Apicurio Registry returned an error: " + ex.getMessage(), ex);
        } catch (Exception ex) {
            if (ex.getMessage() == null) {
                throw new ToolCallException("Request to Apicurio Registry failed with an unknown error. Ask the user to check Apicurio Registry server logs.");
            }
            throw new ToolCallException("Execution failed: " + ex.getMessage(), ex);
        }
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
