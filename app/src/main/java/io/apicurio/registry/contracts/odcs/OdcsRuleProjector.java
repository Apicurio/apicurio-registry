package io.apicurio.registry.contracts.odcs;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ContractRuleDto;
import io.apicurio.registry.storage.dto.ContractRuleSetDto;
import io.apicurio.registry.storage.dto.RuleAction;
import io.apicurio.registry.storage.dto.RuleKind;
import io.apicurio.registry.storage.dto.RuleMode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class OdcsRuleProjector {

    static final String ODCS_RULE_PREFIX = "odcs:";

    @Inject
    @Current
    RegistryStorage storage;

    public int project(OdcsContract contract, String groupId, String artifactId) {
        if (contract.getQuality() == null || contract.getQuality().getAccuracy() == null) {
            return 0;
        }

        var existing = storage.getArtifactContractRuleset(groupId, artifactId);
        List<ContractRuleDto> manual = filterManualRules(existing);
        String source = buildSourceId(contract);

        List<ContractRuleDto> odcsRules = new ArrayList<>();
        int index = 0;
        for (OdcsAccuracyRule rule : contract.getQuality().getAccuracy()) {
            odcsRules.add(toContractRule(rule, source, index++));
        }

        List<ContractRuleDto> merged = new ArrayList<>(odcsRules);
        for (ContractRuleDto m : manual) {
            ContractRuleDto copy = ContractRuleDto.builder()
                    .name(m.getName()).kind(m.getKind()).type(m.getType())
                    .mode(m.getMode()).expr(m.getExpr()).params(m.getParams())
                    .tags(m.getTags()).onSuccess(m.getOnSuccess())
                    .onFailure(m.getOnFailure()).disabled(m.isDisabled())
                    .orderIndex(index++).build();
            merged.add(copy);
        }

        var migration = existing != null && existing.getMigrationRules() != null
                ? existing.getMigrationRules() : List.<ContractRuleDto>of();

        storage.setArtifactContractRuleset(groupId, artifactId,
                ContractRuleSetDto.builder().domainRules(merged)
                        .migrationRules(migration).build());

        return odcsRules.size();
    }

    private ContractRuleDto toContractRule(OdcsAccuracyRule rule, String source, int index) {
        double threshold = rule.getThreshold() != null ? rule.getThreshold() : 1.0;
        String expr = rule.getExpression();
        return ContractRuleDto.builder()
                .name(ODCS_RULE_PREFIX + rule.getName())
                .kind(RuleKind.CONDITION).type("CEL").mode(RuleMode.WRITE)
                .expr(expr)
                .params(Map.of("source", source, "threshold",
                        String.valueOf(threshold)))
                .onFailure(mapThresholdToAction(threshold))
                .onSuccess(RuleAction.NONE).disabled(false).orderIndex(index)
                .build();
    }

    private List<ContractRuleDto> filterManualRules(ContractRuleSetDto existing) {
        if (existing == null || existing.getDomainRules() == null) {
            return List.of();
        }
        return existing.getDomainRules().stream()
                .filter(r -> !r.getName().startsWith(ODCS_RULE_PREFIX)).toList();
    }

    private String buildSourceId(OdcsContract contract) {
        String id = contract.getId() != null ? contract.getId() : "unknown";
        String version = contract.getInfo() != null && contract.getInfo().getVersion() != null
                ? contract.getInfo().getVersion() : "unknown";
        return "odcs:" + id + ":" + version;
    }

    static RuleAction mapThresholdToAction(double threshold) {
        if (threshold >= 1.0) {
            return RuleAction.ERROR;
        } else if (threshold >= 0.95) {
            return RuleAction.DLQ;
        } else {
            return RuleAction.NONE;
        }
    }
}
