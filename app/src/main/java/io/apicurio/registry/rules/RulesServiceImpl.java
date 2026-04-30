package io.apicurio.registry.rules;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.LazyContentList;
import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
        @SuppressWarnings("unchecked")
        Set<RuleType> artifactRules = Collections.EMPTY_SET;
        if (ruleApplicationType == RuleApplicationType.UPDATE) {
            artifactRules = new HashSet<>(storageToUse.getArtifactRules(groupId, artifactId));
        }
        LazyContentList currentContent;
        if (ruleApplicationType == RuleApplicationType.UPDATE) {
            currentContent = new LazyContentList(storageToUse,
                    storageToUse.getEnabledArtifactContentIds(groupId, artifactId));
        } else {
            currentContent = new LazyContentList(storageToUse, Collections.emptyList());
        }

        applyAllRules(storageToUse, groupId, artifactId, artifactType, currentContent, content,
                artifactRules, references, resolvedReferences);
    }

    @Override
    public void applyRules(RegistryStorage storageToUse, String groupId, String artifactId,
            String artifactType, TypedContent content, List<TypedContent> existingContent,
            List<ArtifactReference> references, Map<String, TypedContent> resolvedReferences)
            throws RuleViolationException {
        Set<RuleType> artifactRules = new HashSet<>(storageToUse.getArtifactRules(groupId, artifactId));
        applyAllRules(storageToUse, groupId, artifactId, artifactType, existingContent, content,
                artifactRules, references, resolvedReferences);
    }

    private void applyAllRules(RegistryStorage storageToUse, String groupId, String artifactId,
            String artifactType, List<TypedContent> currentContent, TypedContent updatedContent,
            Set<RuleType> artifactRules, List<ArtifactReference> references,
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

        // Apply rules
        for (RuleType ruleType : allRules.keySet()) {
            applyRule(storageToUse, groupId, artifactId, artifactType, currentContent, updatedContent,
                    ruleType, allRules.get(ruleType).getConfiguration(), references, resolvedReferences);
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
                    storage.getEnabledArtifactContentIds(groupId, artifactId));
        }
        applyRule(storage, groupId, artifactId, artifactType, currentContent, content, ruleType,
                ruleConfiguration, references, resolvedReferences);
    }

    private void applyRule(RegistryStorage storageToUse, String groupId, String artifactId,
            String artifactType, List<TypedContent> currentContent, TypedContent updatedContent,
            RuleType ruleType, String ruleConfiguration, List<ArtifactReference> references,
            Map<String, TypedContent> resolvedReferences) {
        RuleExecutor executor = factory.createExecutor(ruleType);
        RuleContext context = RuleContext.builder().groupId(groupId).artifactId(artifactId)
                .artifactType(artifactType).currentContent(currentContent).updatedContent(updatedContent)
                .configuration(ruleConfiguration).references(references)
                .resolvedReferences(resolvedReferences).storage(storageToUse).build();
        executor.execute(context);
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
}
