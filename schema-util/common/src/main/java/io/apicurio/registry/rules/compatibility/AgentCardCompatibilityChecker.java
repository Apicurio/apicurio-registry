package io.apicurio.registry.rules.compatibility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.content.TypedContent;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * Compatibility checker for A2A Agent Card artifacts.
 *
 * Compatibility rules:
 * - BREAKING: Removing a skill
 * - BREAKING: Changing a skill's ID
 * - BREAKING: Removing a capability (streaming, pushNotifications, stateTransitionHistory)
 * - NON-BREAKING: Adding new skills
 * - NON-BREAKING: Adding new capabilities
 * - NON-BREAKING: Updating skill descriptions, tags, or modes
 *
 * @see <a href="https://google.github.io/A2A/">A2A Protocol</a>
 */
public class AgentCardCompatibilityChecker implements CompatibilityChecker {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public CompatibilityExecutionResult testCompatibility(CompatibilityLevel compatibilityLevel,
            List<TypedContent> existingArtifacts, TypedContent proposedArtifact,
            Map<String, TypedContent> resolvedReferences) {
        requireNonNull(compatibilityLevel, "compatibilityLevel MUST NOT be null");
        requireNonNull(existingArtifacts, "existingArtifacts MUST NOT be null");
        requireNonNull(proposedArtifact, "proposedArtifact MUST NOT be null");

        if (compatibilityLevel == CompatibilityLevel.NONE || existingArtifacts.isEmpty()) {
            return CompatibilityExecutionResult.compatible();
        }

        Set<CompatibilityDifference> differences = new HashSet<>();

        try {
            JsonNode proposed = mapper.readTree(proposedArtifact.getContent().content());

            TypedContent lastExisting = existingArtifacts.get(existingArtifacts.size() - 1);
            JsonNode existing = mapper.readTree(lastExisting.getContent().content());

            switch (compatibilityLevel) {
                case BACKWARD:
                case BACKWARD_TRANSITIVE:
                    differences.addAll(checkBackwardCompatibility(existing, proposed));
                    break;
                case FORWARD:
                case FORWARD_TRANSITIVE:
                    differences.addAll(checkBackwardCompatibility(proposed, existing));
                    break;
                case FULL:
                case FULL_TRANSITIVE:
                    differences.addAll(checkBackwardCompatibility(existing, proposed));
                    differences.addAll(checkBackwardCompatibility(proposed, existing));
                    break;
                default:
                    break;
            }

            if (compatibilityLevel == CompatibilityLevel.BACKWARD_TRANSITIVE
                    || compatibilityLevel == CompatibilityLevel.FORWARD_TRANSITIVE
                    || compatibilityLevel == CompatibilityLevel.FULL_TRANSITIVE) {
                for (int i = existingArtifacts.size() - 2; i >= 0; i--) {
                    JsonNode olderExisting = mapper.readTree(existingArtifacts.get(i).getContent().content());
                    if (compatibilityLevel == CompatibilityLevel.BACKWARD_TRANSITIVE) {
                        differences.addAll(checkBackwardCompatibility(olderExisting, proposed));
                    } else if (compatibilityLevel == CompatibilityLevel.FORWARD_TRANSITIVE) {
                        differences.addAll(checkBackwardCompatibility(proposed, olderExisting));
                    } else {
                        differences.addAll(checkBackwardCompatibility(olderExisting, proposed));
                        differences.addAll(checkBackwardCompatibility(proposed, olderExisting));
                    }
                }
            }
        } catch (Exception e) {
            return CompatibilityExecutionResult.incompatible(e);
        }

        return CompatibilityExecutionResult.incompatibleOrEmpty(differences);
    }

    private Set<CompatibilityDifference> checkBackwardCompatibility(JsonNode existing, JsonNode proposed) {
        Set<CompatibilityDifference> differences = new HashSet<>();

        differences.addAll(checkSkillsCompatibility(existing, proposed));
        differences.addAll(checkCapabilitiesCompatibility(existing, proposed));

        return differences;
    }

    private Set<CompatibilityDifference> checkSkillsCompatibility(JsonNode existing, JsonNode proposed) {
        Set<CompatibilityDifference> differences = new HashSet<>();

        if (!existing.has("skills") || !existing.get("skills").isArray()) {
            return differences;
        }

        Set<String> existingSkillIds = new HashSet<>();
        for (JsonNode skill : existing.get("skills")) {
            if (skill.has("id")) {
                existingSkillIds.add(skill.get("id").asText());
            }
        }

        Set<String> proposedSkillIds = new HashSet<>();
        if (proposed.has("skills") && proposed.get("skills").isArray()) {
            for (JsonNode skill : proposed.get("skills")) {
                if (skill.has("id")) {
                    proposedSkillIds.add(skill.get("id").asText());
                }
            }
        }

        for (String existingSkillId : existingSkillIds) {
            if (!proposedSkillIds.contains(existingSkillId)) {
                differences.add(new SimpleCompatibilityDifference(
                        "Skill '" + existingSkillId + "' was removed (breaking change)", "/skills"));
            }
        }

        return differences;
    }

    private Set<CompatibilityDifference> checkCapabilitiesCompatibility(JsonNode existing, JsonNode proposed) {
        Set<CompatibilityDifference> differences = new HashSet<>();

        if (!existing.has("capabilities") || !existing.get("capabilities").isObject()) {
            return differences;
        }

        JsonNode existingCaps = existing.get("capabilities");
        JsonNode proposedCaps = proposed.has("capabilities") ? proposed.get("capabilities") : null;

        differences.addAll(checkCapabilityRemoved(existingCaps, proposedCaps, "streaming"));
        differences.addAll(checkCapabilityRemoved(existingCaps, proposedCaps, "pushNotifications"));
        differences.addAll(checkCapabilityRemoved(existingCaps, proposedCaps, "stateTransitionHistory"));

        return differences;
    }

    private Set<CompatibilityDifference> checkCapabilityRemoved(JsonNode existingCaps, JsonNode proposedCaps,
            String capabilityName) {
        Set<CompatibilityDifference> differences = new HashSet<>();

        if (existingCaps.has(capabilityName) && existingCaps.get(capabilityName).asBoolean()) {
            boolean proposedHasCapability = proposedCaps != null && proposedCaps.has(capabilityName)
                    && proposedCaps.get(capabilityName).asBoolean();
            if (!proposedHasCapability) {
                differences.add(new SimpleCompatibilityDifference(
                        "Capability '" + capabilityName + "' was removed (breaking change)", "/capabilities"));
            }
        }

        return differences;
    }
}
