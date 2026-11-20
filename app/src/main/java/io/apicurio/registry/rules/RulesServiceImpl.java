package io.apicurio.registry.rules;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.contracts.compatibility.CompatibilityGroupService;
import io.apicurio.registry.metrics.OTelMetricsProvider;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.LazyContentList;
import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.types.VersionState;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Implements the {@link RulesService} interface.
 */
@ApplicationScoped
public class RulesServiceImpl implements RulesService {

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    RuleExecutorFactory factory;

    @Inject
    RulesProperties rulesProperties;

    @Inject
    OTelMetricsProvider otelMetrics;

    @Inject
    ArtifactTypeUtilProviderFactory providerFactory;

    /**
     * @see io.apicurio.registry.rules.RulesService#applyRules(String, String, String, TypedContent,
     *      RuleApplicationType, List, Map)
     */
    @Override
    public void applyRules(String groupId, String artifactId, String artifactType, TypedContent content,
            RuleApplicationType ruleApplicationType, List<ArtifactReference> references,
            Map<String, TypedContent> resolvedReferences) throws RuleViolationException {
        applyRules(storage, groupId, artifactId, artifactType, content, ruleApplicationType,
                references, resolvedReferences);
    }

    @Override
    public void applyRules(RegistryStorage storageToUse, String groupId, String artifactId,
            String artifactType, TypedContent content, RuleApplicationType ruleApplicationType,
            List<ArtifactReference> references, Map<String, TypedContent> resolvedReferences)
            throws RuleViolationException {
        applyRules(storageToUse, groupId, artifactId, null, artifactType, content, ruleApplicationType,
                references, resolvedReferences);
    }

    @Override
    public void applyRulesForNewVersion(String groupId, String artifactId, String version, String artifactType,
            TypedContent content, RuleApplicationType ruleApplicationType, List<ArtifactReference> references,
            Map<String, TypedContent> resolvedReferences) throws RuleViolationException {
        applyRules(storage, groupId, artifactId, version, artifactType, content, ruleApplicationType,
                references, resolvedReferences);
    }

    private void applyRules(RegistryStorage storageToUse, String groupId, String artifactId, String newVersion,
            String artifactType, TypedContent content, RuleApplicationType ruleApplicationType,
            List<ArtifactReference> references, Map<String, TypedContent> resolvedReferences) {
        Set<RuleType> artifactRules = Collections.emptySet();
        if (ruleApplicationType == RuleApplicationType.UPDATE) {
            artifactRules = new HashSet<>(storageToUse.getArtifactRules(groupId, artifactId));
        }
        LazyContentList currentContent;
        if (ruleApplicationType == RuleApplicationType.UPDATE) {
            currentContent = new LazyContentList(storageToUse,
                    comparableContentIds(storageToUse, groupId, artifactId));
        } else {
            currentContent = new LazyContentList(storageToUse, Collections.emptyList());
        }

        applyAllRules(storageToUse, groupId, artifactId, newVersion, artifactType, currentContent, content,
                artifactRules, references, resolvedReferences);
    }

    @Override
    public void applyRules(RegistryStorage storageToUse, String groupId, String artifactId, String version,
            String artifactType, TypedContent content, List<TypedContent> existingContent,
            List<ArtifactReference> references, Map<String, TypedContent> resolvedReferences)
            throws RuleViolationException {
        Set<RuleType> artifactRules = new HashSet<>(storageToUse.getArtifactRules(groupId, artifactId));
        applyAllRules(storageToUse, groupId, artifactId, version, artifactType, existingContent, content,
                artifactRules, references, resolvedReferences);
    }

    private void applyAllRules(RegistryStorage storageToUse, String groupId, String artifactId,
            String artifactType, List<TypedContent> currentContent, TypedContent updatedContent,
            Set<RuleType> artifactRules, List<ArtifactReference> references,
            Map<String, TypedContent> resolvedReferences) {
        applyAllRules(storageToUse, groupId, artifactId, null, artifactType, currentContent, updatedContent,
                artifactRules, references, resolvedReferences);
    }

    private void applyAllRules(RegistryStorage storageToUse, String groupId, String artifactId,
            String newVersion, String artifactType, List<TypedContent> currentContent,
            TypedContent updatedContent, Set<RuleType> artifactRules, List<ArtifactReference> references,
            Map<String, TypedContent> resolvedReferences) {

        Map<RuleType, RuleConfigurationDto> allRules = new HashMap<>();

        // Get the group rules (we already have the artifact rules)
        Set<RuleType> groupRules = storageToUse.isGroupExists(groupId)
            ? new HashSet<>(storageToUse.getGroupRules(groupId)) : Set.of();
        // Get the global rules
        Set<RuleType> globalRules = new HashSet<>(storageToUse.getGlobalRules());
        // Get the configured default global rules
        Set<RuleType> defaultGlobalRules = rulesProperties.getDefaultGlobalRules();

        // Build the map of rules to apply (may be empty)
        List.of(RuleType.values()).forEach(rt -> {
            if (artifactRules.contains(rt)) {
                allRules.put(rt, storageToUse.getArtifactRule(groupId, artifactId, rt));
            } else if (groupRules.contains(rt)) {
                allRules.put(rt, storageToUse.getGroupRule(groupId, rt));
            } else if (globalRules.contains(rt)) {
                allRules.put(rt, storageToUse.getGlobalRule(rt));
            } else if (defaultGlobalRules.contains(rt)) {
                allRules.put(rt, rulesProperties.getDefaultGlobalRuleConfiguration(rt));
            }
        });

        // Apply rules (metrics are recorded per-rule in applyRule)
        for (RuleType ruleType : allRules.keySet()) {
            applyRule(storageToUse, groupId, artifactId, newVersion, artifactType, currentContent,
                    updatedContent, ruleType, allRules.get(ruleType).getConfiguration(), references,
                    resolvedReferences);
        }
    }

    @Override
    public void applyRule(String groupId, String artifactId, String artifactType, TypedContent content,
            RuleType ruleType, String ruleConfiguration, RuleApplicationType ruleApplicationType,
            List<ArtifactReference> references, Map<String, TypedContent> resolvedReferences)
            throws RuleViolationException {
        LazyContentList currentContent = null;
        if (ruleApplicationType == RuleApplicationType.UPDATE) {
            currentContent = new LazyContentList(storage,
                    comparableContentIds(storage, groupId, artifactId));
        }
        applyRule(storage, groupId, artifactId, null, artifactType, currentContent, content, ruleType,
                ruleConfiguration, references, resolvedReferences);
    }

    // Metrics are recorded here even during dry-run requests because rule evaluation genuinely
    // executes during dry-run — only artifact/version creation metrics are suppressed.
    private void applyRule(RegistryStorage storageToUse, String groupId, String artifactId,
            String newVersion, String artifactType, List<TypedContent> currentContent, TypedContent updatedContent,
            RuleType ruleType, String ruleConfiguration, List<ArtifactReference> references,
            Map<String, TypedContent> resolvedReferences) {
        RuleExecutor executor = factory.createExecutor(ruleType);
        RuleContext context = RuleContext.builder().groupId(groupId).artifactId(artifactId)
                .artifactType(artifactType).currentContent(currentContent).updatedContent(updatedContent)
                .configuration(ruleConfiguration).references(references)
                .resolvedReferences(resolvedReferences).storage(storageToUse).version(newVersion).build();
        try {
            executor.execute(context);
            otelMetrics.recordRuleEvaluation(ruleType.value(), true);
            if (ruleType == RuleType.VALIDITY) {
                otelMetrics.recordSchemaValidation(artifactType, true);
            }
        } catch (Exception e) {
            otelMetrics.recordRuleEvaluation(ruleType.value(), false);
            if (ruleType == RuleType.VALIDITY) {
                otelMetrics.recordSchemaValidation(artifactType, false);
            }
            throw e;
        }
    }

    @Override
    public void applyRules(String groupId, String artifactId, String artifactVersion, String artifactType,
            TypedContent updatedContent, List<ArtifactReference> references,
            Map<String, TypedContent> resolvedReferences) throws RuleViolationException {
        StoredArtifactVersionDto versionContent = storage.getArtifactVersionContent(groupId, artifactId,
                artifactVersion);
        TypedContent typedVersionContent = TypedContent.create(versionContent.getContent(),
                versionContent.getContentType());
        Set<RuleType> artifactRules = new HashSet<>(storage.getArtifactRules(groupId, artifactId));
        applyAllRules(storage, groupId, artifactId, artifactType,
                Collections.singletonList(typedVersionContent), updatedContent, artifactRules,
                references, resolvedReferences);
    }

    /**
     * The content of the existing versions a new version is checked against: every version that is
     * not disabled, or, when the artifact is in a data contract compatibility group, only the versions
     * created in that group.
     */
    private static List<Long> comparableContentIds(RegistryStorage storageToUse, String groupId,
            String artifactId) {
        Optional<Map.Entry<String, String>> compatibilityGroup = CompatibilityGroupService
                .findCompatibilityGroup(storageToUse.getArtifactMetaData(groupId, artifactId).getLabels());
        if (compatibilityGroup.isEmpty()) {
            return storageToUse.getEnabledArtifactContentIds(groupId, artifactId);
        }
        String key = compatibilityGroup.get().getKey();
        String group = compatibilityGroup.get().getValue();
        return storageToUse
                .getArtifactVersions(groupId, artifactId, EnumSet.complementOf(EnumSet.of(VersionState.DISABLED)))
                .stream().map(version -> storageToUse.getArtifactVersionMetaData(groupId, artifactId, version))
                .filter(meta -> meta.getLabels() != null && group.equals(meta.getLabels().get(key)))
                .sorted(Comparator.comparingInt(ArtifactVersionMetaDataDto::getVersionOrder))
                .map(ArtifactVersionMetaDataDto::getContentId).toList();
    }
}
