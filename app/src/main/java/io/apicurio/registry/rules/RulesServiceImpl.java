package io.apicurio.registry.rules;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.metrics.OTelMetricsProvider;
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
    OTelMetricsProvider otelMetrics;

    @Inject
    ArtifactTypeUtilProviderFactory providerFactory;

    @Override
    public void applyRules(RuleApplicationContext context) throws RuleViolationException {
        Objects.requireNonNull(context, "context must not be null");
        RegistryStorage storageToUse = context.getStorage() != null ? context.getStorage() : storage;

        List<TypedContent> currentContent = resolveCurrentContent(context, storageToUse);
        Set<RuleType> artifactRules;

        if (context.getExistingContent() != null || context.getArtifactVersion() != null || context.getRuleApplicationType() == RuleApplicationType.UPDATE) {
            artifactRules = new HashSet<>(storageToUse.getArtifactRules(context.getGroupId(), context.getArtifactId()));
        } else {
            artifactRules = Collections.emptySet();
        }

        applyAllRules(storageToUse, context.getGroupId(), context.getArtifactId(), context.getArtifactType(),
                currentContent, context.getContent(), artifactRules, context.getReferences(),
                context.getResolvedReferences());
    }

    @Override
    public void applyRule(RuleApplicationContext context) throws RuleViolationException {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(context.getRuleType(), "ruleType must not be null for applyRule");
        RegistryStorage storageToUse = context.getStorage() != null ? context.getStorage() : storage;

        List<TypedContent> currentContent = resolveCurrentContent(context, storageToUse);

        RuleContext ruleContext = RuleContext.builder()
                .storage(storageToUse)
                .groupId(context.getGroupId())
                .artifactId(context.getArtifactId())
                .artifactType(context.getArtifactType())
                .currentContent(currentContent)
                .updatedContent(context.getContent())
                .configuration(context.getRuleConfiguration())
                .references(context.getReferences())
                .resolvedReferences(context.getResolvedReferences())
                .build();

        executeSingleRule(context.getRuleType(), ruleContext);
    }

    private List<TypedContent> resolveCurrentContent(RuleApplicationContext context, RegistryStorage storageToUse) {
        if (context.getExistingContent() != null) {
            return context.getExistingContent();
        } else if (context.getArtifactVersion() != null) {
            StoredArtifactVersionDto versionContent = storageToUse.getArtifactVersionContent(
                    context.getGroupId(), context.getArtifactId(), context.getArtifactVersion());
            TypedContent typedVersionContent = TypedContent.create(versionContent.getContent(),
                    versionContent.getContentType());
            return Collections.singletonList(typedVersionContent);
        } else if (context.getRuleApplicationType() == RuleApplicationType.UPDATE) {
            return new LazyContentList(storageToUse,
                    storageToUse.getEnabledArtifactContentIds(context.getGroupId(), context.getArtifactId()));
        } else {
            return new LazyContentList(storageToUse, Collections.emptyList());
        }
    }

    @Override
    public void applyRules(String groupId, String artifactId, String artifactType, TypedContent content,
            RuleApplicationType ruleApplicationType, List<ArtifactReference> references,
            Map<String, TypedContent> resolvedReferences) throws RuleViolationException {
        applyRules(RuleApplicationContext.builder()
                .groupId(groupId)
                .artifactId(artifactId)
                .artifactType(artifactType)
                .content(content)
                .ruleApplicationType(ruleApplicationType)
                .references(references)
                .resolvedReferences(resolvedReferences)
                .build());
    }

    @Override
    public void applyRules(RegistryStorage storageToUse, String groupId, String artifactId,
            String artifactType, TypedContent content, RuleApplicationType ruleApplicationType,
            List<ArtifactReference> references, Map<String, TypedContent> resolvedReferences)
            throws RuleViolationException {
        applyRules(RuleApplicationContext.builder()
                .storage(storageToUse)
                .groupId(groupId)
                .artifactId(artifactId)
                .artifactType(artifactType)
                .content(content)
                .ruleApplicationType(ruleApplicationType)
                .references(references)
                .resolvedReferences(resolvedReferences)
                .build());
    }

    @Override
    public void applyRules(RegistryStorage storageToUse, String groupId, String artifactId,
            String artifactType, TypedContent content, List<TypedContent> existingContent,
            List<ArtifactReference> references, Map<String, TypedContent> resolvedReferences)
            throws RuleViolationException {
        applyRules(RuleApplicationContext.builder()
                .storage(storageToUse)
                .groupId(groupId)
                .artifactId(artifactId)
                .artifactType(artifactType)
                .content(content)
                .existingContent(existingContent)
                .ruleApplicationType(RuleApplicationType.UPDATE)
                .references(references)
                .resolvedReferences(resolvedReferences)
                .build());
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

        // Apply rules (metrics are recorded per-rule in executeSingleRule)
        for (RuleType ruleType : allRules.keySet()) {
            RuleContext ruleContext = RuleContext.builder()
                    .storage(storageToUse)
                    .groupId(groupId)
                    .artifactId(artifactId)
                    .artifactType(artifactType)
                    .currentContent(currentContent)
                    .updatedContent(updatedContent)
                    .configuration(allRules.get(ruleType).getConfiguration())
                    .references(references)
                    .resolvedReferences(resolvedReferences)
                    .build();
            executeSingleRule(ruleType, ruleContext);
        }
    }

    @Override
    public void applyRule(String groupId, String artifactId, String artifactType, TypedContent content,
            RuleType ruleType, String ruleConfiguration, RuleApplicationType ruleApplicationType,
            List<ArtifactReference> references, Map<String, TypedContent> resolvedReferences)
            throws RuleViolationException {
        applyRule(RuleApplicationContext.builder()
                .groupId(groupId)
                .artifactId(artifactId)
                .artifactType(artifactType)
                .content(content)
                .ruleType(ruleType)
                .ruleConfiguration(ruleConfiguration)
                .ruleApplicationType(ruleApplicationType)
                .references(references)
                .resolvedReferences(resolvedReferences)
                .build());
    }

    // Metrics are recorded here even during dry-run requests because rule evaluation genuinely
    // executes during dry-run — only artifact/version creation metrics are suppressed.
    private void executeSingleRule(RuleType ruleType, RuleContext context) {
        RuleExecutor executor = factory.createExecutor(ruleType);
        try {
            executor.execute(context);
            otelMetrics.recordRuleEvaluation(ruleType.value(), true);
            if (ruleType == RuleType.VALIDITY) {
                otelMetrics.recordSchemaValidation(context.getArtifactType(), true);
            }
        } catch (Exception e) {
            otelMetrics.recordRuleEvaluation(ruleType.value(), false);
            if (ruleType == RuleType.VALIDITY) {
                otelMetrics.recordSchemaValidation(context.getArtifactType(), false);
            }
            throw e;
        }
    }

    @Override
    public void applyRules(String groupId, String artifactId, String artifactVersion, String artifactType,
            TypedContent updatedContent, List<ArtifactReference> references,
            Map<String, TypedContent> resolvedReferences) throws RuleViolationException {
        applyRules(RuleApplicationContext.builder()
                .groupId(groupId)
                .artifactId(artifactId)
                .artifactVersion(artifactVersion)
                .artifactType(artifactType)
                .content(updatedContent)
                .ruleApplicationType(RuleApplicationType.UPDATE)
                .references(references)
                .resolvedReferences(resolvedReferences)
                .build());
    }
}
