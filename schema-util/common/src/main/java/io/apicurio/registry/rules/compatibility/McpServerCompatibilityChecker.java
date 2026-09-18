package io.apicurio.registry.rules.compatibility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.content.TypedContent;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Checks whether existing installation/connection choices still exist in a new manifest. */
public class McpServerCompatibilityChecker extends AbstractCompatibilityChecker<SimpleCompatibilityDifference> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    protected Set<SimpleCompatibilityDifference> isBackwardsCompatibleWith(String existing, String proposed,
            Map<String, TypedContent> resolvedReferences) {
        Set<SimpleCompatibilityDifference> differences = new HashSet<>();
        try {
            JsonNode before = MAPPER.readTree(existing);
            JsonNode after = MAPPER.readTree(proposed);
            if (!before.path("name").equals(after.path("name"))) {
                differences.add(new SimpleCompatibilityDifference("Server identity changed", "/name"));
            }
            for (String field : new String[] { "packages", "remotes" }) {
                int index = 0;
                for (JsonNode entry : before.path(field)) {
                    boolean retained = false;
                    for (JsonNode candidate : after.path(field)) {
                        if (sameConnection(entry, candidate, "packages".equals(field))) {
                            retained = true;
                            break;
                        }
                    }
                    if (!retained) {
                        differences.add(new SimpleCompatibilityDifference(
                                "Existing installation or connection option was removed or changed", "/" + field + "/" + index));
                    }
                    index++;
                }
            }
        } catch (Exception e) {
            differences.add(new SimpleCompatibilityDifference("Invalid MCP server definition", ""));
        }
        return differences;
    }

    private boolean sameConnection(JsonNode before, JsonNode after, boolean packaged) {
        if (packaged) {
            // Package version/hash changes are expected on upgrade; install identity, transport and
            // configuration inputs must remain stable for existing consumers.
            for (String key : new String[] { "registryType", "identifier", "registryBaseUrl", "transport",
                    "runtimeHint", "runtimeArguments", "packageArguments", "environmentVariables" }) {
                if (!before.path(key).equals(after.path(key))) {
                    return false;
                }
            }
            return true;
        }
        for (String key : new String[] { "type", "url", "headers", "variables" }) {
            if (!before.path(key).equals(after.path(key))) {
                return false;
            }
        }
        return true;
    }
}
