package io.apicurio.registry.contracts.migration;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.contracts.rules.MigrationExecutor;
import io.apicurio.registry.contracts.rules.RuleDefinition;
import io.apicurio.registry.contracts.rules.RuleExecutionEngine;
import io.apicurio.registry.contracts.rules.RuleExecutionResult;
import io.apicurio.registry.contracts.rules.RuleExecutionService;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ContractRuleSetDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;

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

        List<String> allVersions = storage.getArtifactVersions(groupId, artifactId);

        if (!allVersions.contains(fromVersion)) {
            throw new BadRequestException("Version not found: " + fromVersion);
        }
        if (!allVersions.contains(toVersion)) {
            throw new BadRequestException("Version not found: " + toVersion);
        }

        MigrationExecutor executor = new MigrationExecutor(engine);

        return executor.execute(allVersions, fromVersion, toVersion, record,
                (version, mode) -> loadMigrationRules(groupId, artifactId, version, mode));
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
