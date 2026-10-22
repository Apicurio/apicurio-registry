package io.apicurio.registry.rules.app.governance;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.contracts.ContractLabels;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.rules.violation.RuleViolation;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
@Logged
public class GovernanceRuleExecutor {

    @Inject
    @Current
    RegistryStorage storage;

    public void check(String groupId, String artifactId, String contractId,
            GovernanceLevel level) throws RuleViolationException {
        if (level == GovernanceLevel.NONE) {
            return;
        }

        ArtifactMetaDataDto meta = storage.getArtifactMetaData(groupId, artifactId);
        Map<String, String> labels = meta.getLabels() != null ? meta.getLabels() : Map.of();
        String prefix = ContractLabels.contractPrefix(contractId);

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

        if (!violations.isEmpty()) {
            throw new RuleViolationException("Governance rule violations found",
                    null, level.name(), violations);
        }
    }
}
