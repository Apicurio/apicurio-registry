package io.apicurio.registry.contracts.quality;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.contracts.ContractLabels;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.ContractRuleSetDto;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;

@ApplicationScoped
public class QualityScoreCalculator {

    private static final float WEIGHT_COMPLETENESS = 0.30f;
    private static final float WEIGHT_COMPLIANCE = 0.40f;
    private static final float WEIGHT_STABILITY = 0.30f;

    @Inject
    @Current
    RegistryStorage storage;

    public QualityScore calculate(String groupId, String artifactId,
            String contractId) {
        ArtifactMetaDataDto meta = storage.getArtifactMetaData(groupId, artifactId);
        Map<String, String> labels = meta.getLabels() != null ? meta.getLabels() : Map.of();
        String prefix = ContractLabels.contractPrefix(contractId);

        float completeness = calculateCompleteness(meta, labels, prefix);
        float compliance = calculateCompliance(groupId, artifactId, labels, prefix);
        float stability = calculateStability(groupId, artifactId, labels, prefix);

        float overall = completeness * WEIGHT_COMPLETENESS
                + compliance * WEIGHT_COMPLIANCE
                + stability * WEIGHT_STABILITY;

        return QualityScore.builder()
                .overall(overall)
                .completeness(completeness)
                .compliance(compliance)
                .stability(stability)
                .build();
    }

    private float calculateCompleteness(ArtifactMetaDataDto meta,
            Map<String, String> labels, String prefix) {
        int total = 0;
        int present = 0;

        total++;
        if (meta.getDescription() != null && !meta.getDescription().isBlank()) {
            present++;
        }
        total++;
        if (labels.containsKey(prefix + ContractLabels.SUFFIX_OWNER_TEAM)) {
            present++;
        }
        total++;
        if (labels.containsKey(prefix + ContractLabels.SUFFIX_SUPPORT_CONTACT)) {
            present++;
        }
        total++;
        if (labels.containsKey(prefix + ContractLabels.SUFFIX_CLASSIFICATION)) {
            present++;
        }
        total++;
        if (labels.containsKey(prefix + ContractLabels.SUFFIX_SLA_AVAILABILITY)) {
            present++;
        }
        total++;
        if (labels.containsKey(prefix + ContractLabels.SUFFIX_OWNER_DOMAIN)) {
            present++;
        }
        return total > 0 ? (float) present / total : 0f;
    }

    private float calculateCompliance(String groupId, String artifactId,
            Map<String, String> labels, String prefix) {
        int total = 0;
        int present = 0;

        total++;
        ContractRuleSetDto ruleset = storage.getArtifactContractRuleset(groupId,
                artifactId);
        if (ruleset != null && ruleset.getDomainRules() != null
                && !ruleset.getDomainRules().isEmpty()) {
            present++;
        }
        total++;
        if (labels.containsKey(prefix + ContractLabels.SUFFIX_STATUS)) {
            present++;
        }
        return total > 0 ? (float) present / total : 0f;
    }

    private float calculateStability(String groupId, String artifactId,
            Map<String, String> labels, String prefix) {
        int total = 0;
        int present = 0;

        total++;
        if ("STABLE".equals(labels.get(prefix + ContractLabels.SUFFIX_STATUS))) {
            present++;
        }
        total++;
        try {
            var versions = storage.getArtifactVersions(groupId, artifactId);
            if (versions != null && versions.size() > 1) {
                present++;
            }
        } catch (ArtifactNotFoundException e) {
            // artifact doesn't exist yet — no version history
        }
        total++;
        if (labels.containsKey(prefix + ContractLabels.SUFFIX_ID)) {
            present++;
        }
        return total > 0 ? (float) present / total : 0f;
    }
}
