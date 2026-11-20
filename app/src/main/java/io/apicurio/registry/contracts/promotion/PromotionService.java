package io.apicurio.registry.contracts.promotion;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.contracts.ContractLabels;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.ContractStatus;
import io.apicurio.registry.storage.dto.PromotionStage;
import io.apicurio.registry.storage.error.ContractStateConflictException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;

import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class PromotionService {

    @Inject
    @Current
    RegistryStorage storage;

    public PromotionStage promote(String groupId, String artifactId,
            String contractId, PromotionStage targetStage) {
        String prefix = ContractLabels.contractPrefix(contractId);

        ArtifactMetaDataDto meta = storage.getArtifactMetaData(groupId, artifactId);
        Map<String, String> labels = meta.getLabels() != null ? meta.getLabels() : Map.of();

        String stageKey = prefix + ContractLabels.SUFFIX_STAGE;
        String statusKey = prefix + ContractLabels.SUFFIX_STATUS;
        PromotionStage currentStage = parseStage(labels.get(stageKey));
        if (currentStage == targetStage) {
            // Typically a concurrent promotion to the same stage that committed first: a conflict, like
            // losing the compare-and-set below, rather than an invalid transition
            throw new ContractStateConflictException("The contract of artifact '" + groupId + "/" + artifactId
                    + "' is already at stage " + targetStage + ".");
        }
        validateTransition(currentStage, targetStage, parseStatus(labels.get(statusKey)));

        // The promotion was validated against the current stage and status: apply it only if
        // neither changed since (a concurrent request gets a 409 conflict).
        Map<String, String> expected = new HashMap<>();
        expected.put(stageKey, labels.get(stageKey));
        expected.put(statusKey, labels.get(statusKey));
        storage.compareAndSetArtifactLabels(groupId, artifactId, expected, Map.of(stageKey, targetStage.name()));

        return targetStage;
    }

    /**
     * The contract's current promotion stage on the artifact, or {@code null} if it was never
     * promoted.
     */
    public PromotionStage currentStage(String groupId, String artifactId, String contractId) {
        Map<String, String> labels = storage.getArtifactMetaData(groupId, artifactId).getLabels();
        return labels == null ? null
                : parseStage(labels.get(ContractLabels.key(contractId, ContractLabels.SUFFIX_STAGE)));
    }

    /**
     * Checks a stage change: forward only, one stage at a time starting from DEV, and PROD only
     * for a STABLE contract.
     *
     * @throws BadRequestException if the change is not allowed
     */
    public void validateTransition(PromotionStage current, PromotionStage target, ContractStatus status) {
        if (current != null && current.ordinal() >= target.ordinal()) {
            throw new BadRequestException(
                    "Cannot promote from " + current + " to " + target
                            + ". Only forward promotion is allowed.");
        }
        int next = current == null ? 0 : current.ordinal() + 1;
        if (target.ordinal() > next) {
            throw new BadRequestException(
                    "Cannot skip stages. Promote to " + PromotionStage.values()[next] + " first.");
        }
        if (target == PromotionStage.PROD && status != ContractStatus.STABLE) {
            throw new BadRequestException(
                    "PROD promotion requires STABLE contract status. Current: " + status);
        }
    }

    private static PromotionStage parseStage(String value) {
        return value != null ? PromotionStage.valueOf(value) : null;
    }

    private static ContractStatus parseStatus(String value) {
        try {
            return value != null ? ContractStatus.valueOf(value) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
