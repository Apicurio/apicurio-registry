package io.apicurio.registry.rules.app.integrity;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.model.GAV;
import io.apicurio.registry.references.ReferenceGraphWalker;
import io.apicurio.registry.references.ReferenceLoader;
import io.apicurio.registry.references.ReferenceNode;
import io.apicurio.registry.references.ReferenceResolutionConfig;
import io.apicurio.registry.references.ResolvedReferences;
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
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.storage.error.VersionNotFoundException;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProvider;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
@Logged
public class IntegrityRuleExecutor implements RuleExecutor {

    @Inject
    ArtifactTypeUtilProviderFactory factory;

    @Inject
    @Current
    RegistryStorage defaultStorage;

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

        // Make sure that the included references all actually exist in the registry, and that they point to
        // artifact types this type is allowed to reference.
        if (levels.contains(IntegrityLevel.FULL) || levels.contains(IntegrityLevel.REFS_EXIST)) {
            validateReferencesExist(context);
            validateReferenceTargetTypes(context);
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

    private RegistryStorage resolveStorage(RuleContext context) {
        return context.getStorage() != null ? context.getStorage() : defaultStorage;
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

    private void validateReferenceTargetTypes(RuleContext context) throws RuleViolationException {
        List<ArtifactReference> references = context.getReferences();
        if (references == null || references.isEmpty()) {
            return;
        }
        Optional<Set<String>> allowedTypes = factory.getArtifactTypeProvider(context.getArtifactType())
                .getContentValidator().getAllowedReferenceTargetTypes();
        if (allowedTypes.isEmpty()) {
            return;
        }

        RegistryStorage storageToUse = resolveStorage(context);
        Set<RuleViolation> causes = new HashSet<>();
        for (ArtifactReference ref : references) {
            String targetType;
            try {
                targetType = storageToUse.getArtifactVersionMetaData(ref.getGroupId(), ref.getArtifactId(),
                        ref.getVersion()).getArtifactType();
            } catch (ArtifactNotFoundException | VersionNotFoundException e) {
                // A missing target is reported by the existence check.
                continue;
            }
            if (!allowedTypes.get().contains(targetType)) {
                RuleViolation violation = new RuleViolation();
                violation.setContext(ref.getName());
                violation.setDescription(String.format(
                        "Referenced artifact (%s/%s @ %s) is of type %s, which %s artifacts cannot reference.",
                        ref.getGroupId(), ref.getArtifactId(), ref.getVersion(), targetType,
                        context.getArtifactType()));
                causes.add(violation);
            }
        }
        if (!causes.isEmpty()) {
            throw new RuleViolationException("Referenced artifact type is not allowed.", RuleType.INTEGRITY,
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
     * does not yet exist in storage, no existing version can reference it, so we only need to check for
     * cycles within the existing reference graph that would be introduced by the new references.
     * <p>
     * Each declared reference is checked independently (its own walk, its own cycle search), so a cycle
     * reachable from more than one of them is still reported once per offending reference, matching the
     * granularity callers already see from {@link RuleViolation#getContext()}.
     */
    private void checkForCircularReferences(RuleContext context) throws RuleViolationException {
        List<ArtifactReference> references = context.getReferences();
        if (references == null || references.isEmpty()) {
            return;
        }

        RegistryStorage storageToUse = resolveStorage(context);
        Set<RuleViolation> causes = new HashSet<>();

        for (ArtifactReference ref : references) {
            Optional<List<GAV>> cycle = findCycleInReferenceGraph(storageToUse, ref);

            if (cycle.isPresent()) {
                RuleViolation violation = new RuleViolation();
                violation.setContext(ref.getName());
                violation.setDescription(String.format("Circular reference detected in reference graph: %s",
                        renderCycle(cycle.get())));
                causes.add(violation);
            }
        }

        if (!causes.isEmpty()) {
            throw new RuleViolationException("Circular artifact reference(s) detected.", RuleType.INTEGRITY,
                    IntegrityLevel.NO_CIRCULAR_REFERENCES.name(), causes);
        }
    }

    /**
     * Walks the reference graph reachable from {@code ref} via the shared {@link ReferenceGraphWalker}
     * (the same traversal every other reference consumer uses, per the milestone epic #10635) and looks
     * for a cycle in it.
     * <p>
     * The loader treats a missing referenced artifact/version ({@link ArtifactNotFoundException} or
     * {@link VersionNotFoundException}) as "not found" — recorded by the walker, not a cycle, and not an
     * error. Any other storage failure propagates out of {@link ReferenceLoader#loadBatch}, out of
     * {@link ReferenceGraphWalker#walk}, and ultimately out of this rule's execution, instead of being
     * reported as "no cycle" (#9095).
     *
     * @return the cycle, if one exists, as the coordinates on its path (see
     *         {@link ResolvedReferences#findCycle()})
     */
    private Optional<List<GAV>> findCycleInReferenceGraph(RegistryStorage storageToUse, ArtifactReference ref) {
        ReferenceLoader loader = coordinates -> loadBatch(storageToUse, coordinates);
        ArtifactReferenceDto rootReference = ArtifactReferenceDto.builder().groupId(ref.getGroupId())
                .artifactId(ref.getArtifactId()).version(ref.getVersion()).name(ref.getName()).build();
        ResolvedReferences resolved = ReferenceGraphWalker.walk(List.of(rootReference), loader,
                ReferenceResolutionConfig.maxDepth());
        return resolved.findCycle();
    }

    private Map<GAV, ReferenceNode> loadBatch(RegistryStorage storageToUse, Collection<GAV> coordinates) {
        Map<GAV, ReferenceNode> loaded = new LinkedHashMap<>();
        for (GAV coordinate : coordinates) {
            StoredArtifactVersionDto content;
            try {
                content = storageToUse.getArtifactVersionContent(coordinate.getRawGroupIdWithNull(),
                        coordinate.getRawArtifactId(), coordinate.getRawVersionId());
            } catch (ArtifactNotFoundException | VersionNotFoundException e) {
                // The referenced artifact/version doesn't exist (yet): no cycle possible through this
                // path. Any other failure (e.g. a storage outage) is not caught here - see below.
                continue;
            }
            loaded.put(coordinate,
                    ReferenceNode.builder().coordinates(coordinate).references(content.getReferences())
                            .build());
        }
        return loaded;
    }

    /**
     * Renders a cycle the same way the walker-based cycle check used to, before it moved onto
     * {@link GAV}: {@code group/artifact@version}, chained with {@code " -> "}, with the closing node
     * (which repeats the one the path returns to) suffixed {@code " (cycle)"}.
     */
    private String renderCycle(List<GAV> cycle) {
        List<String> rendered = cycle.stream().map(IntegrityRuleExecutor::formatArtifactRef)
                .collect(Collectors.toCollection(java.util.ArrayList::new));
        int lastIndex = rendered.size() - 1;
        rendered.set(lastIndex, rendered.get(lastIndex) + " (cycle)");
        return String.join(" -> ", rendered);
    }

    private static String formatArtifactRef(GAV gav) {
        return gav.getRawGroupIdWithDefaultString() + "/" + gav.getRawArtifactId() + "@"
                + gav.getRawVersionId();
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
