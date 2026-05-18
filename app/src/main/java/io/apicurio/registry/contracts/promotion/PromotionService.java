package io.apicurio.registry.contracts.promotion;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.contracts.ContractLabels;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.PromotionStage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;

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

        String currentStageStr = labels.get(prefix + ContractLabels.SUFFIX_STAGE);
        PromotionStage currentStage = currentStageStr != null
                ? PromotionStage.valueOf(currentStageStr) : null;

        validateTransition(currentStage, targetStage, labels, prefix);

        storage.mergeArtifactLabels(groupId, artifactId, prefix + ContractLabels.SUFFIX_STAGE,
                Map.of(prefix + ContractLabels.SUFFIX_STAGE, targetStage.name()));

        return targetStage;
    }

    private void validateTransition(PromotionStage current, PromotionStage target,
            Map<String, String> labels, String prefix) {
        if (current != null && current.ordinal() >= target.ordinal()) {
            throw new BadRequestException(
                    "Cannot promote from " + current + " to " + target
                            + ". Only forward promotion is allowed.");
        }
        if (current != null && target.ordinal() - current.ordinal() > 1) {
            throw new BadRequestException(
                    "Cannot skip stages. Promote to "
                            + PromotionStage.values()[current.ordinal() + 1]
                            + " first.");
        }
        if (target == PromotionStage.PROD) {
            String status = labels.get(prefix + ContractLabels.SUFFIX_STATUS);
            if (!"STABLE".equals(status)) {
                throw new BadRequestException(
                        "PROD promotion requires STABLE contract status. Current: "
                                + status);
            }
        }
    }
}
