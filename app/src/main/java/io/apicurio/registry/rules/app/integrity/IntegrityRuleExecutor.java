package io.apicurio.registry.rules.app.integrity;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.RuleContext;
import io.apicurio.registry.rules.RuleExecutor;
import io.apicurio.registry.rules.integrity.IntegrityLevel;
import io.apicurio.registry.rules.violation.RuleViolation;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.rules.validity.ContentValidator;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProvider;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
@Logged
public class IntegrityRuleExecutor implements RuleExecutor {

    @Inject
    ArtifactTypeUtilProviderFactory factory;

    @Inject
    @Current
    RegistryStorage storage;

    /**
     * @see io.apicurio.registry.rules.RuleExecutor#execute(io.apicurio.registry.rules.RuleContext)
     */
    @Override
    public void execute(RuleContext context) throws RuleViolationException {
        Set<IntegrityLevel> levels = parseConfig(context.getConfiguration());

        // Make sure that the user has included mappings for all references in the content of the artifact.
        if (levels.contains(IntegrityLevel.FULL) || levels.contains(IntegrityLevel.ALL_REFS_MAPPED)) {
            // Not yet implemented - needs artifact type specific logic to extract the full list of
            // references that must be mapped from the artifact version content
            verifyAllReferencesHaveMappings(context);
        }

        // Make sure that the included references all actually exist in the registry.
        if (levels.contains(IntegrityLevel.FULL) || levels.contains(IntegrityLevel.REFS_EXIST)) {
            validateReferencesExist(context);
        }

        // Make sure that there are no duplicate mappings
        if (levels.contains(IntegrityLevel.FULL) || levels.contains(IntegrityLevel.NO_DUPLICATES)) {
            checkForDuplicateReferences(context);
        }

        // Make sure that the references do not create a circular dependency
        if (levels.contains(IntegrityLevel.FULL)
                || levels.contains(IntegrityLevel.NO_CIRCULAR_REFERENCES)) {
            checkForCircularReferences(context);
        }
    }

    private void verifyAllReferencesHaveMappings(RuleContext context) throws RuleViolationException {
        ArtifactTypeUtilProvider artifactTypeProvider = factory
                .getArtifactTypeProvider(context.getArtifactType());
        ContentValidator validator = artifactTypeProvider.getContentValidator();
        validator.validateReferences(context.getUpdatedContent(), context.getReferences());
    }

    private void validateReferencesExist(RuleContext context) throws RuleViolationException {
        List<ArtifactReference> references = context.getReferences();
        if (references == null) {
            references = List.of();
        }
        Map<String, TypedContent> resolvedReferences = context.getResolvedReferences();

        Set<RuleViolation> causes = new HashSet<>();
        references.forEach(ref -> {
            if (!resolvedReferences.containsKey(ref.getName())) {
                RuleViolation violation = new RuleViolation();
                violation.setContext(ref.getName());
                violation.setDescription(
                        String.format("Referenced artifact (%s/%s @ %s) does not yet exist in the registry.",
                                ref.getGroupId(), ref.getArtifactId(), ref.getVersion()));
                causes.add(violation);
            }
        });
        if (!causes.isEmpty()) {
            throw new RuleViolationException("Referenced artifact does not exist.", RuleType.INTEGRITY,
                    IntegrityLevel.REFS_EXIST.name(), causes);
        }

    }

    private void checkForDuplicateReferences(RuleContext context) throws RuleViolationException {
        List<ArtifactReference> references = context.getReferences();
        if (references != null && references.size() > 1) {
            Set<String> refNames = new HashSet<>();
            Set<RuleViolation> causes = new HashSet<>();
            references.forEach(ref -> {
                if (refNames.contains(ref.getName())) {
                    RuleViolation violation = new RuleViolation();
                    violation.setContext(ref.getName());
                    violation.setDescription(
                            "Duplicate mapping for artifact reference with name: " + ref.getName());
                    causes.add(violation);
                }
                refNames.add(ref.getName());
            });
            if (!causes.isEmpty()) {
                throw new RuleViolationException("Duplicate artifact reference(s) detected.",
                        RuleType.INTEGRITY, IntegrityLevel.NO_DUPLICATES.name(), causes);
            }
        }
    }

    /**
     * Checks if adding the references to the current artifact would create a circular dependency. A circular
     * dependency occurs when an artifact A references artifact B (directly or transitively), and artifact B
     * references artifact A (directly or transitively).
     */
    private void checkForCircularReferences(RuleContext context) throws RuleViolationException {
        List<ArtifactReference> references = context.getReferences();
        if (references == null || references.isEmpty()) {
            return;
        }

        String targetGroupId = context.getGroupId();
        String targetArtifactId = context.getArtifactId();
        String targetArtifactKey = createArtifactKey(targetGroupId, targetArtifactId);

        Set<RuleViolation> causes = new HashSet<>();

        for (ArtifactReference ref : references) {
            // Track the path for cycle detection
            Set<String> visited = new HashSet<>();
            visited.add(targetArtifactKey);

            // Check if following this reference eventually leads back to the target artifact
            List<String> cyclePath = findCyclePath(ref.getGroupId(), ref.getArtifactId(), ref.getVersion(),
                    targetArtifactKey, visited);

            if (cyclePath != null) {
                RuleViolation violation = new RuleViolation();
                violation.setContext(ref.getName());
                violation.setDescription(String.format(
                        "Circular reference detected: %s/%s -> %s/%s @ %s creates a cycle. Path: %s",
                        targetGroupId != null ? targetGroupId : "default", targetArtifactId,
                        ref.getGroupId() != null ? ref.getGroupId() : "default", ref.getArtifactId(),
                        ref.getVersion(), String.join(" -> ", cyclePath)));
                causes.add(violation);
            }
        }

        if (!causes.isEmpty()) {
            throw new RuleViolationException("Circular artifact reference(s) detected.", RuleType.INTEGRITY,
                    IntegrityLevel.NO_CIRCULAR_REFERENCES.name(), causes);
        }
    }

    /**
     * Recursively follows references from the given artifact version to detect if it eventually references
     * the target artifact.
     *
     * @return the cycle path if a cycle is found, null otherwise
     */
    private List<String> findCyclePath(String groupId, String artifactId, String version,
            String targetArtifactKey, Set<String> visited) {
        String currentArtifactKey = createArtifactKey(groupId, artifactId);

        // If we've reached the target, we've found a cycle
        if (currentArtifactKey.equals(targetArtifactKey)) {
            List<String> path = new java.util.ArrayList<>();
            path.add(formatArtifactRef(groupId, artifactId, version));
            return path;
        }

        // If we've already visited this artifact (but it's not the target), skip to avoid infinite loops
        if (visited.contains(currentArtifactKey)) {
            return null;
        }

        visited.add(currentArtifactKey);

        try {
            StoredArtifactVersionDto content = storage.getArtifactVersionContent(groupId, artifactId,
                    version);
            List<ArtifactReferenceDto> refs = content.getReferences();

            if (refs != null) {
                for (ArtifactReferenceDto ref : refs) {
                    List<String> cyclePath = findCyclePath(ref.getGroupId(), ref.getArtifactId(),
                            ref.getVersion(), targetArtifactKey, visited);
                    if (cyclePath != null) {
                        cyclePath.add(0, formatArtifactRef(groupId, artifactId, version));
                        return cyclePath;
                    }
                }
            }
        } catch (Exception e) {
            // Artifact/version doesn't exist yet or is inaccessible, no cycle possible through this path
        }

        return null;
    }

    private String createArtifactKey(String groupId, String artifactId) {
        return (groupId != null ? groupId : "default") + "/" + artifactId;
    }

    private String formatArtifactRef(String groupId, String artifactId, String version) {
        return (groupId != null ? groupId : "default") + "/" + artifactId + "@" + version;
    }

    /**
     * @param configuration
     */
    private Set<IntegrityLevel> parseConfig(String configuration) {
        Set<IntegrityLevel> levels = new HashSet<>();
        if (configuration != null) {
            String[] split = configuration.split(",");
            for (String cvalue : split) {
                levels.add(IntegrityLevel.valueOf(cvalue.trim()));
            }
        }
        return levels;
    }

}
