package io.apicurio.registry.contracts.migration;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.contracts.rules.RuleDefinition;
import io.apicurio.registry.contracts.rules.RuleExecutionEngine;
import io.apicurio.registry.contracts.rules.RuleExecutionResult;
import io.apicurio.registry.contracts.rules.RuleExecutionService;
import io.apicurio.registry.contracts.rules.RuleViolation;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ContractRuleSetDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class MigrationRuleService {

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    RuleExecutionEngine engine;

    public RuleExecutionResult executeMigration(String groupId, String artifactId,
            String fromVersion, String toVersion, Map<String, Object> record) {

        List<String> path = computeMigrationPath(groupId, artifactId, fromVersion, toVersion);
        if (path.size() < 2) {
            return new RuleExecutionResult(true, record, List.of(), 0, 0);
        }

        boolean isUpgrade = path.indexOf(fromVersion) < path.indexOf(toVersion);
        String mode = isUpgrade ? "UPGRADE" : "DOWNGRADE";

        List<String> hops = isUpgrade ? path : new ArrayList<>(path);
        if (!isUpgrade) {
            Collections.reverse(hops);
        }

        Map<String, Object> currentRecord = record;
        int totalExecuted = 0;
        int totalFailed = 0;
        List<RuleViolation> allViolations = new ArrayList<>();

        for (int i = 0; i < hops.size() - 1; i++) {
            String stepVersion = hops.get(i + 1);
            List<RuleDefinition> migrationRules = loadMigrationRules(
                    groupId, artifactId, stepVersion, mode);

            if (migrationRules.isEmpty()) {
                continue;
            }

            RuleExecutionResult stepResult = engine.execute(
                    migrationRules, mode, currentRecord);
            totalExecuted += stepResult.getExecutedRules();
            totalFailed += stepResult.getFailedRules();
            allViolations.addAll(stepResult.getViolations());

            if (!stepResult.isPassed()) {
                return new RuleExecutionResult(false, currentRecord,
                        allViolations, totalExecuted, totalFailed);
            }

            if (stepResult.getTransformedRecord() != null) {
                currentRecord = stepResult.getTransformedRecord();
            }
        }

        return new RuleExecutionResult(true, currentRecord,
                allViolations, totalExecuted, totalFailed);
    }

    public List<String> computeMigrationPath(String groupId, String artifactId,
            String fromVersion, String toVersion) {
        List<String> allVersions = storage.getArtifactVersions(groupId, artifactId);

        int fromIdx = allVersions.indexOf(fromVersion);
        int toIdx = allVersions.indexOf(toVersion);

        if (fromIdx < 0) {
            throw new BadRequestException("Version not found: " + fromVersion);
        }
        if (toIdx < 0) {
            throw new BadRequestException("Version not found: " + toVersion);
        }

        int start = Math.min(fromIdx, toIdx);
        int end = Math.max(fromIdx, toIdx);
        return allVersions.subList(start, end + 1);
    }

    private List<RuleDefinition> loadMigrationRules(String groupId, String artifactId,
            String version, String mode) {
        ContractRuleSetDto ruleset = storage.getVersionContractRuleset(
                groupId, artifactId, version);
        if (ruleset == null || ruleset.getMigrationRules() == null) {
            return List.of();
        }

        return ruleset.getMigrationRules().stream()
                .filter(r -> r.getMode() != null && r.getMode().name().equals(mode))
                .map(RuleExecutionService::toRuleDefinition)
                .toList();
    }
}
