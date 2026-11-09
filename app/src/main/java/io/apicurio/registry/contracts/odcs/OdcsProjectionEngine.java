package io.apicurio.registry.contracts.odcs;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.contracts.ContractLabels;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ContractRuleSetDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class OdcsProjectionEngine {

    private static final Logger log = LoggerFactory.getLogger(OdcsProjectionEngine.class);

    @Inject
    @Current
    RegistryStorage storage;
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
     * What a contract has projected onto one artifact, to put back exactly if a later projection
     * fails: the contract's labels (including state set through the API), the artifact's contract
     * ruleset, and the contract's field tags on each version.
     */
    public record Snapshot(String groupId, String artifactId, Map<String, String> labels,
            ContractRuleSetDto ruleset, Map<String, Map<String, String>> tags) {
    }

    /**
     * Captures the contract's projection on an existing artifact.
     */
    public Snapshot snapshot(String contractId, String groupId, String artifactId) {
        String prefix = ContractLabels.contractPrefix(contractId);
        Map<String, String> labels = new LinkedHashMap<>();
        Map<String, String> current = storage.getArtifactMetaData(groupId, artifactId).getLabels();
        if (current != null) {
            current.forEach((key, value) -> {
                if (key.startsWith(prefix)) {
                    labels.put(key, value);
                }
            });
        }
        return new Snapshot(groupId, artifactId, labels,
                storage.getArtifactContractRuleset(groupId, artifactId),
                tagProjector.tagsByVersion(contractId, groupId, artifactId));
    }

    /**
     * Puts a captured projection back: the contract's labels and field tags as they were (a
     * version without tags in the snapshot loses any tags written since), and the artifact's
     * contract ruleset.
     */
    public void restore(String contractId, Snapshot snapshot) {
        String groupId = snapshot.groupId();
        String artifactId = snapshot.artifactId();
        storage.mergeArtifactLabels(groupId, artifactId, ContractLabels.contractPrefix(contractId),
                snapshot.labels());
        storage.setArtifactContractRuleset(groupId, artifactId, snapshot.ruleset() != null
                ? snapshot.ruleset()
                : ContractRuleSetDto.builder().domainRules(List.of()).migrationRules(List.of()).build());
        for (String version : storage.getArtifactVersions(groupId, artifactId, Set.of())) {
            tagProjector.replaceTags(contractId, groupId, artifactId, version,
                    snapshot.tags().getOrDefault(version, Map.of()));
        }
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
