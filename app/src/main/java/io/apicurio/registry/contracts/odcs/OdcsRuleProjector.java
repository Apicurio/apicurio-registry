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

    public int project(OdcsContract contract, String contractId, String groupId,
            String artifactId) {
        if (contract.getQuality() == null || contract.getQuality().getAccuracy() == null) {
            return 0;
        }

        String rulePrefix = ODCS_RULE_PREFIX + contractId + ":";
        var existing = storage.getArtifactContractRuleset(groupId, artifactId);

        List<ContractRuleDto> otherRules = new ArrayList<>();
        if (existing != null && existing.getDomainRules() != null) {
            for (ContractRuleDto rule : existing.getDomainRules()) {
                if (!rule.getName().startsWith(rulePrefix)) {
                    otherRules.add(rule);
                }
            }
        }

        String source = "odcs:" + contractId + ":"
                + (contract.getInfo() != null && contract.getInfo().getVersion() != null
                        ? contract.getInfo().getVersion()
                        : "unknown");

        List<ContractRuleDto> odcsRules = new ArrayList<>();
        int index = 0;
        for (OdcsAccuracyRule rule : contract.getQuality().getAccuracy()) {
            double threshold = rule.getThreshold() != null ? rule.getThreshold() : 1.0;
            odcsRules.add(ContractRuleDto.builder()
                    .name(rulePrefix + rule.getName())
                    .kind(RuleKind.CONDITION).type("CEL").mode(RuleMode.WRITE)
                    .expr(rule.getExpression())
                    .params(Map.of("source", source, "threshold",
                            String.valueOf(threshold)))
                    .onFailure(mapThresholdToAction(threshold))
                    .onSuccess(RuleAction.NONE).disabled(false).orderIndex(index++)
                    .build());
        }

        List<ContractRuleDto> merged = new ArrayList<>(odcsRules);
        for (ContractRuleDto other : otherRules) {
            ContractRuleDto copy = ContractRuleDto.builder()
                    .name(other.getName()).kind(other.getKind()).type(other.getType())
                    .mode(other.getMode()).expr(other.getExpr()).params(other.getParams())
                    .tags(other.getTags()).onSuccess(other.getOnSuccess())
                    .onFailure(other.getOnFailure()).disabled(other.isDisabled())
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
