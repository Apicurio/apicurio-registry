package io.apicurio.registry.contracts.rules;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.contracts.tags.TagExtractor;
import io.apicurio.registry.contracts.tags.TagExtractorFactory;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.ContractRuleDto;
import io.apicurio.registry.storage.dto.ContractRuleSetDto;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@ApplicationScoped
public class RuleExecutionService {

    private static final Logger log = LoggerFactory.getLogger(RuleExecutionService.class);

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    RuleExecutionEngine engine;

    @Inject
    TagExtractorFactory tagExtractorFactory;

    public RuleExecutionResult execute(String groupId, String artifactId,
            String version, String mode, Map<String, Object> record) {
        ContractRuleSetDto ruleset = loadMergedRuleset(groupId, artifactId, version);
        if (ruleset == null || ruleset.getDomainRules() == null) {
            return new RuleExecutionResult(true, null, List.of(), 0, 0);
        }

        List<RuleDefinition> rules = ruleset.getDomainRules().stream()
                .map(RuleExecutionService::toRuleDefinition)
                .toList();

        Map<String, Set<String>> fieldTags = null;
        boolean hasFieldRules = rules.stream().anyMatch(r -> "CEL_FIELD".equals(r.getType()));

        if (hasFieldRules && version != null) {
            fieldTags = resolveFieldTags(groupId, artifactId, version);
        }

        return engine.execute(rules, mode, record, fieldTags);
    }

    private Map<String, Set<String>> resolveFieldTags(String groupId, String artifactId, String version) {
        try {
            ArtifactVersionMetaDataDto metadata = storage.getArtifactVersionMetaData(groupId, artifactId, version);
            if (metadata == null || metadata.getArtifactType() == null) {
                return null;
            }
            String artifactType = metadata.getArtifactType();
            Optional<TagExtractor> extractorOpt = tagExtractorFactory.getExtractor(artifactType);
            if (extractorOpt.isEmpty()) {
                log.warn("CEL_FIELD rule configured but no tag extractor available for artifact type '{}' — rule will not be evaluated", artifactType);
                return null;
            }
            StoredArtifactVersionDto versionContent = storage.getArtifactVersionContent(groupId, artifactId, version);
            if (versionContent != null && versionContent.getContent() != null) {
                return extractorOpt.get().extractTags(versionContent.getContent());
            }
        } catch (Exception e) {
            log.warn("Failed to resolve field tags for artifact {}/{}/v{}: {}", groupId, artifactId, version, e.getMessage());
        }
        return null;
    }

    private ContractRuleSetDto loadMergedRuleset(String groupId, String artifactId,
            String version) {
        ContractRuleSetDto globalRules = storage.getGlobalContractRuleset();
        ContractRuleSetDto artifactRules = storage.getArtifactContractRuleset(
                groupId, artifactId);

        ContractRuleSetDto merged = globalRules;
        if (merged == null) {
            merged = artifactRules;
        } else if (artifactRules != null) {
            merged = mergeRulesets(merged, artifactRules);
        }

        if (version == null) {
            return merged;
        }
        ContractRuleSetDto versionRules = storage.getVersionContractRuleset(
                groupId, artifactId, version);
        if (merged == null) {
            return versionRules;
        }
        if (versionRules == null) {
            return merged;
        }
        return mergeRulesets(merged, versionRules);
    }

    private ContractRuleSetDto mergeRulesets(ContractRuleSetDto artifact,
            ContractRuleSetDto version) {
        List<ContractRuleDto> merged = new ArrayList<>();
        if (artifact.getDomainRules() != null) {
            merged.addAll(artifact.getDomainRules());
        }
        if (version.getDomainRules() != null) {
            for (ContractRuleDto vRule : version.getDomainRules()) {
                merged.removeIf(r -> r.getName().equals(vRule.getName()));
                merged.add(vRule);
            }
        }
        return ContractRuleSetDto.builder()
                .domainRules(merged)
                .migrationRules(version.getMigrationRules() != null
                        ? version.getMigrationRules()
                        : artifact.getMigrationRules())
                .build();
    }

    public static RuleDefinition toRuleDefinition(ContractRuleDto dto) {
        RuleDefinition def = new RuleDefinition();
        def.setName(dto.getName());
        def.setKind(dto.getKind() != null ? dto.getKind().name() : null);
        def.setType(dto.getType());
        def.setMode(dto.getMode() != null ? dto.getMode().name() : null);
        def.setExpr(dto.getExpr());
        def.setParams(dto.getParams());
        def.setTags(dto.getTags());
        def.setOnSuccess(dto.getOnSuccess() != null ? dto.getOnSuccess().name() : null);
        def.setOnFailure(dto.getOnFailure() != null ? dto.getOnFailure().name() : null);
        def.setDisabled(dto.isDisabled());
        def.setOrderIndex(dto.getOrderIndex());
        return def;
    }
}
