package io.apicurio.registry.rules.app.governance;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.contracts.ContractLabels;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.rules.RuleContext;
import io.apicurio.registry.rules.RuleExecutor;
import io.apicurio.registry.rules.violation.RuleViolation;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.types.RuleType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
@Logged
public class GovernanceRuleExecutor implements RuleExecutor {

    @Inject
    @Current
    RegistryStorage storage;

    /**
     * Applies a GOVERNANCE rule to a new version. The rule checks the contract metadata of the
     * artifact, so it only applies to existing artifacts that carry contract metadata
     * ({@code contract.*} labels); other artifacts are not data contracts and are not affected.
     */
    @Override
    public void execute(RuleContext context) throws RuleViolationException {
        GovernanceLevel level = GovernanceLevel.valueOf(context.getConfiguration());
        if (level == GovernanceLevel.NONE) {
            return;
        }
        RegistryStorage storageToUse = context.getStorage() != null ? context.getStorage() : storage;
        if (!storageToUse.isArtifactExists(context.getGroupId(), context.getArtifactId())) {
            return;
        }
        Map<String, String> labels = labelsOf(
                storageToUse.getArtifactMetaData(context.getGroupId(), context.getArtifactId()));
        if (labels.keySet().stream().noneMatch(key -> key.startsWith(ContractLabels.PREFIX))) {
            return;
        }
        String contractId = ContractLabels.findContractId(labels);
        check(labels, ContractLabels.prefixFor(contractId), level);
    }

    public void check(String groupId, String artifactId, String contractId,
            GovernanceLevel level) throws RuleViolationException {
        if (level == GovernanceLevel.NONE) {
            return;
        }
        Map<String, String> labels = labelsOf(storage.getArtifactMetaData(groupId, artifactId));
        check(labels, ContractLabels.contractPrefix(contractId), level);
    }

    private static Map<String, String> labelsOf(ArtifactMetaDataDto meta) {
        return meta.getLabels() != null ? meta.getLabels() : Map.of();
    }

    private static void check(Map<String, String> labels, String prefix, GovernanceLevel level)
            throws RuleViolationException {
        Set<RuleViolation> violations = new HashSet<>();

        String status = labels.get(prefix + ContractLabels.SUFFIX_STATUS);
        if ("DEPRECATED".equals(status)) {
            violations.add(new RuleViolation(
                    "Updates to deprecated contracts are not allowed",
                    prefix + ContractLabels.SUFFIX_STATUS));
        }

        String owner = labels.get(prefix + ContractLabels.SUFFIX_OWNER_TEAM);
        if (owner == null || owner.isBlank()) {
            violations.add(new RuleViolation(
                    "Contract owner team is required",
                    prefix + ContractLabels.SUFFIX_OWNER_TEAM));
        }

        if (level == GovernanceLevel.FULL) {
            checkFullGovernance(labels, prefix, status, violations);
        }

        if (!violations.isEmpty()) {
            throw new RuleViolationException("Governance rule violations found",
                    RuleType.GOVERNANCE, level.name(), violations);
        }
    }

    private static void checkFullGovernance(Map<String, String> labels, String prefix, String status,
            Set<RuleViolation> violations) {
        String classification = labels.get(prefix + ContractLabels.SUFFIX_CLASSIFICATION);
        if (classification == null || classification.isBlank()) {
            violations.add(new RuleViolation(
                    "Data classification is required",
                    prefix + ContractLabels.SUFFIX_CLASSIFICATION));
        }

        String contact = labels.get(prefix + ContractLabels.SUFFIX_SUPPORT_CONTACT);
        if (contact == null || contact.isBlank()) {
            violations.add(new RuleViolation(
                    "Support contact is required",
                    prefix + ContractLabels.SUFFIX_SUPPORT_CONTACT));
        }

        String stage = labels.get(prefix + ContractLabels.SUFFIX_STAGE);
        if ("PROD".equals(stage) && !"STABLE".equals(status)) {
            violations.add(new RuleViolation(
                    "PROD promotion requires STABLE status",
                    prefix + ContractLabels.SUFFIX_STAGE));
        }
    }
}
