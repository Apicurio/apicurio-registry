package io.apicurio.registry.contracts.rules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

public class MigrationExecutor {

    private final RuleExecutionEngine engine;

    public MigrationExecutor(RuleExecutionEngine engine) {
        this.engine = engine;
    }

    /**
     * Executes multi-hop migration from fromVersion to toVersion.
     *
     * @param allVersions ordered list of all versions for the artifact
     * @param fromVersion the version of the record being migrated
     * @param toVersion the target version
     * @param record the record to transform
     * @param rulesLoader function that loads migration rules for a given (version, mode) pair
     * @return the result with the transformed record
     */
    public RuleExecutionResult execute(List<String> allVersions, String fromVersion,
            String toVersion, Map<String, Object> record,
            BiFunction<String, String, List<RuleDefinition>> rulesLoader) {

        List<String> path = computePath(allVersions, fromVersion, toVersion);
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
            List<RuleDefinition> migrationRules = rulesLoader.apply(stepVersion, mode);

            if (migrationRules.isEmpty()) {
                continue;
            }

            RuleExecutionResult stepResult = engine.execute(migrationRules, mode, currentRecord);
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

    static List<String> computePath(List<String> allVersions, String fromVersion, String toVersion) {
        int fromIdx = allVersions.indexOf(fromVersion);
        int toIdx = allVersions.indexOf(toVersion);

        if (fromIdx < 0 || toIdx < 0) {
            return List.of();
        }

        int start = Math.min(fromIdx, toIdx);
        int end = Math.max(fromIdx, toIdx);
        return allVersions.subList(start, end + 1);
    }
}
