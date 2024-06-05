package io.apicurio.registry.rules;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.LazyContentList;
import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implements the {@link RulesService} interface.
 *
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
     * @see io.apicurio.registry.rules.RulesService#applyRules(String, String, String, TypedContent, RuleApplicationType, List, Map) 
     */
    @Override
    public void applyRules(String groupId, String artifactId, String artifactType, TypedContent content,
                           RuleApplicationType ruleApplicationType, List<ArtifactReference> references,
                           Map<String, TypedContent> resolvedReferences) throws RuleViolationException {
        @SuppressWarnings("unchecked")
        List<RuleType> rules = Collections.EMPTY_LIST;
        if (ruleApplicationType == RuleApplicationType.UPDATE) {
            rules = storage.getArtifactRules(groupId, artifactId);
        }
        LazyContentList currentContent = null;
        if (ruleApplicationType == RuleApplicationType.UPDATE) {
            currentContent = new LazyContentList(storage, storage.getEnabledArtifactContentIds(groupId, artifactId));
        } else {
            currentContent = new LazyContentList(storage, Collections.emptyList());
        }

        applyGlobalAndArtifactRules(groupId, artifactId, artifactType, currentContent, content, rules, references, resolvedReferences);
    }

    private void applyGlobalAndArtifactRules(String groupId, String artifactId, String artifactType,
                                             List<TypedContent> currentContent, TypedContent updatedContent,
                                             List<RuleType> artifactRules, List<ArtifactReference> references,
                                             Map<String, TypedContent> resolvedReferences) {

        Map<RuleType, RuleConfigurationDto> globalOrArtifactRulesMap = artifactRules.stream()
                .collect(Collectors.toMap(ruleType -> ruleType, ruleType -> storage.getArtifactRule(groupId, artifactId, ruleType)));

        if (globalOrArtifactRulesMap.isEmpty()) {
            List<RuleType> globalRules = storage.getGlobalRules();
            globalOrArtifactRulesMap = globalRules.stream()
                    .collect(Collectors.toMap(ruleType -> ruleType, storage::getGlobalRule));

            // Add any default global rules to the map (after filtering out any global rules from artifactStore)
            Map<RuleType, RuleConfigurationDto> filteredDefaultGlobalRulesMap = rulesProperties.getFilteredDefaultGlobalRules(globalRules).stream()
                    .collect(Collectors.toMap(ruleType -> ruleType, rulesProperties::getDefaultGlobalRuleConfiguration));
            globalOrArtifactRulesMap.putAll(filteredDefaultGlobalRulesMap);
        }

        if (globalOrArtifactRulesMap.isEmpty()) {
            return;
        }

        for (RuleType ruleType : globalOrArtifactRulesMap.keySet()) {
            applyRule(groupId, artifactId, artifactType, currentContent, updatedContent, ruleType,
                    globalOrArtifactRulesMap.get(ruleType).getConfiguration(), references, resolvedReferences);
        }
    }

    /**
     * @see io.apicurio.registry.rules.RulesService#applyRule(String, String, String, TypedContent, RuleType, String, RuleApplicationType, List, Map)
     */
    @Override
    public void applyRule(String groupId, String artifactId, String artifactType, TypedContent content,
                          RuleType ruleType, String ruleConfiguration, RuleApplicationType ruleApplicationType,
                          List<ArtifactReference> references, Map<String, TypedContent> resolvedReferences)
            throws RuleViolationException {
        LazyContentList currentContent = null;
        if (ruleApplicationType == RuleApplicationType.UPDATE) {
            currentContent = new LazyContentList(storage, storage.getEnabledArtifactContentIds(groupId, artifactId));
        }
        applyRule(groupId, artifactId, artifactType, currentContent, content, ruleType, ruleConfiguration,
                references, resolvedReferences);
    }

    /**
     * Applies a single rule.  Throws an exception if the rule is violated.
     */
    private void applyRule(String groupId, String artifactId, String artifactType, List<TypedContent> currentContent,
                           TypedContent updatedContent, RuleType ruleType, String ruleConfiguration,
                           List<ArtifactReference> references, Map<String, TypedContent> resolvedReferences) {
        RuleExecutor executor = factory.createExecutor(ruleType);
        RuleContext context = RuleContext.builder()
                .groupId(groupId)
                .artifactId(artifactId)
                .artifactType(artifactType)
                .currentContent(currentContent)
                .updatedContent(updatedContent)
                .configuration(ruleConfiguration)
                .references(references)
                .resolvedReferences(resolvedReferences)
                .build();
        executor.execute(context);
    }

    /**
     * @see io.apicurio.registry.rules.RulesService#applyRules(String, String, String, String, TypedContent, List, Map)
     */
    @Override
    public void applyRules(String groupId, String artifactId, String artifactVersion, String artifactType,
                           TypedContent updatedContent, List<ArtifactReference> references,
                           Map<String, TypedContent> resolvedReferences) throws RuleViolationException {
        StoredArtifactVersionDto versionContent = storage.getArtifactVersionContent(groupId, artifactId, artifactVersion);
        TypedContent typedVersionContent = TypedContent.create(versionContent.getContent(), versionContent.getContentType());
        applyGlobalAndArtifactRules(groupId, artifactId, artifactType, Collections.singletonList(typedVersionContent),
                updatedContent, storage.getArtifactRules(groupId, artifactId), references, resolvedReferences);
    }
}
