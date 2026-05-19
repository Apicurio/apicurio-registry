package io.apicurio.registry.contracts.rules;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ContractRuleDto;
import io.apicurio.registry.storage.dto.ContractRuleSetDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class RuleExecutionService {

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    RuleExecutionEngine engine;

    public RuleExecutionResult execute(String groupId, String artifactId,
            String version, String mode, Map<String, Object> record) {
        ContractRuleSetDto ruleset = loadMergedRuleset(groupId, artifactId, version);
        if (ruleset == null || ruleset.getDomainRules() == null) {
            return new RuleExecutionResult(true, null, List.of(), 0, 0);
        }

        List<RuleDefinition> rules = ruleset.getDomainRules().stream()
                .map(RuleExecutionService::toRuleDefinition)
                .toList();

        return engine.execute(rules, mode, record);
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
