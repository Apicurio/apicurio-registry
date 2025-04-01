package io.apicurio.utils.test.raml.microsvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rules.compatibility.CompatibilityChecker;
import io.apicurio.registry.rules.compatibility.CompatibilityExecutionResult;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;

import java.util.List;
import java.util.Map;

public class RamlCompatibilityChecker implements CompatibilityChecker {

    private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    @Override
    public CompatibilityExecutionResult testCompatibility(CompatibilityLevel compatibilityLevel,
            List<TypedContent> existingArtifacts, TypedContent proposedArtifact, Map<String, TypedContent> resolvedReferences) {
        if (existingArtifacts == null || existingArtifacts.isEmpty()) {
            return CompatibilityExecutionResult.compatible();
        }

        try {
            JsonNode proposedRoot = mapper.readTree(proposedArtifact.getContent().content());
            JsonNode existingRoot = mapper.readTree(existingArtifacts.get(0).getContent().content());

            String proposedVersion = proposedRoot.get("version").toString();
            String existingVersion = existingRoot.get("version").toString();

            if (proposedVersion.equals(existingVersion)) {
                return CompatibilityExecutionResult.incompatible("Expected new version number but found identical versions: " + proposedVersion);
            }
        } catch (Throwable t) {
            return CompatibilityExecutionResult.incompatible("Error during compatibility check: " + t.getMessage());
        }

        return CompatibilityExecutionResult.compatible();
    }

}
