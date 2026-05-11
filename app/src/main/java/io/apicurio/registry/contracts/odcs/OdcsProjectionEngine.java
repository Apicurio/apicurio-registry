package io.apicurio.registry.contracts.odcs;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class OdcsProjectionEngine {

    private static final Logger log = LoggerFactory.getLogger(OdcsProjectionEngine.class);

    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    @Inject
    OdcsLabelProjector labelProjector;
    @Inject
    OdcsRuleProjector ruleProjector;
    @Inject
    OdcsTagProjector tagProjector;

    public OdcsProjectionResult project(OdcsContract contract, String contractId,
            String groupId, String artifactId) {
        String lockKey = (groupId != null ? groupId : "") + "/" + artifactId;
        Object lock = locks.computeIfAbsent(lockKey, k -> new Object());

        synchronized (lock) {
            try {
                return doProject(contract, contractId, groupId, artifactId);
            } finally {
                locks.remove(lockKey);
            }
        }
    }

    private OdcsProjectionResult doProject(OdcsContract contract, String contractId,
            String groupId, String artifactId) {
        var result = OdcsProjectionResult.builder().build();

        result.setLabelsApplied(
                labelProjector.project(contract, contractId, groupId, artifactId));
        result.setRulesApplied(
                ruleProjector.project(contract, contractId, groupId, artifactId));
        result.setTagsApplied(tagProjector.project(contract, contractId, groupId,
                artifactId, result.getWarnings()));

        log.info("Projected ODCS contract {} onto {}/{}: {} rules, {} labels, {} tags",
                contractId, groupId, artifactId, result.getRulesApplied(),
                result.getLabelsApplied(), result.getTagsApplied());

        return result;
    }
}
