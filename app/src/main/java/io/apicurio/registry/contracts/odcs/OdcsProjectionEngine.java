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

        result.setLabelsApplied(labelProjector.project(contract, contractId, groupId,
                artifactId, result.getWarnings()));
        result.setRulesApplied(
                ruleProjector.project(contract, contractId, groupId, artifactId));
        result.setTagsApplied(tagProjector.project(contract, contractId, groupId,
                artifactId, result.getWarnings()));

        log.info("Projected ODCS contract {} onto {}/{}: {} rules, {} labels, {} tags",
                contractId, groupId, artifactId, result.getRulesApplied(),
                result.getLabelsApplied(), result.getTagsApplied());

        return result;
    }

    /**
     * Removes everything the contract projected onto the artifact: its labels (including state
     * set through the API), its rules, and its field tags on every version.
     */
    public void remove(String contractId, String groupId, String artifactId) {
        labelProjector.remove(contractId, groupId, artifactId);
        ruleProjector.remove(contractId, groupId, artifactId);
        tagProjector.remove(contractId, groupId, artifactId);
        log.info("Removed the projection of ODCS contract {} from {}/{}", contractId, groupId,
                artifactId);
    }
}
