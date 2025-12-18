package io.apicurio.registry.rules.app.integrity;

import io.apicurio.registry.cdi.Current;
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
     * dependency occurs when following the reference chain leads to a cycle. Since the version being created
     * does not yet exist in storage, no existing version can reference it, so we only need to check:
     * 1. Direct self-references (a version referencing itself)
     * 2. Cycles within the existing reference graph that would be introduced by the new references
     */
    private void checkForCircularReferences(RuleContext context) throws RuleViolationException {
        List<ArtifactReference> references = context.getReferences();
        if (references == null || references.isEmpty()) {
            return;
        }

        Set<RuleViolation> causes = new HashSet<>();

        for (ArtifactReference ref : references) {
            // Track visited versions to detect cycles in the reference graph
            Set<String> visited = new HashSet<>();

            // Check if following this reference leads to a cycle in the existing reference graph
            List<String> cyclePath = findCycleInReferenceGraph(ref.getGroupId(), ref.getArtifactId(),
                    ref.getVersion(), visited);

            if (cyclePath != null) {
                RuleViolation violation = new RuleViolation();
                violation.setContext(ref.getName());
                violation.setDescription(String.format("Circular reference detected in reference graph: %s",
                        String.join(" -> ", cyclePath)));
                causes.add(violation);
            }
        }

        if (!causes.isEmpty()) {
            throw new RuleViolationException("Circular artifact reference(s) detected.", RuleType.INTEGRITY,
                    IntegrityLevel.NO_CIRCULAR_REFERENCES.name(), causes);
        }
    }

    /**
     * Recursively follows references from the given artifact version to detect if there is a cycle in the
     * reference graph.
     *
     * @return the cycle path if a cycle is found, null otherwise
     */
    private List<String> findCycleInReferenceGraph(String groupId, String artifactId, String version,
            Set<String> visited) {
        String currentVersionKey = createVersionKey(groupId, artifactId, version);

        // If we've already visited this version, we've found a cycle
        if (visited.contains(currentVersionKey)) {
            List<String> path = new java.util.ArrayList<>();
            path.add(formatArtifactRef(groupId, artifactId, version) + " (cycle)");
            return path;
        }

        visited.add(currentVersionKey);

        try {
            StoredArtifactVersionDto content = storage.getArtifactVersionContent(groupId, artifactId,
                    version);
            List<ArtifactReferenceDto> refs = content.getReferences();

            if (refs != null) {
                for (ArtifactReferenceDto ref : refs) {
                    List<String> cyclePath = findCycleInReferenceGraph(ref.getGroupId(), ref.getArtifactId(),
                            ref.getVersion(), visited);
                    if (cyclePath != null) {
                        cyclePath.add(0, formatArtifactRef(groupId, artifactId, version));
                        return cyclePath;
                    }
                }
            }
        } catch (Exception e) {
            // Artifact/version doesn't exist yet or is inaccessible, no cycle possible through this path
        }

        // Remove from visited for other paths (backtracking)
        visited.remove(currentVersionKey);

        return null;
    }

    private String createVersionKey(String groupId, String artifactId, String version) {
        return (groupId != null ? groupId : "default") + "/" + artifactId + "@" + version;
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
