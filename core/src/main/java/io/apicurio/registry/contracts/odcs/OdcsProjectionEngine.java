package io.apicurio.registry.contracts.odcs;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class OdcsProjectionEngine {

    private static final Logger log = LoggerFactory.getLogger(OdcsProjectionEngine.class);

    @Inject
    OdcsLabelProjector labelProjector;
    @Inject
    OdcsRuleProjector ruleProjector;
    @Inject
    OdcsTagProjector tagProjector;

    public OdcsProjectionResult project(OdcsContract contract, String contractId,
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
