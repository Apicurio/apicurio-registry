package io.apicurio.registry.contracts.compatibility;

import io.apicurio.registry.contracts.ContractLabels;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.cdi.Current;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class CompatibilityGroupService {

    @Inject
    @Current
    RegistryStorage storage;

    public String getCompatibilityGroup(String groupId, String artifactId, String contractId) {
        ArtifactMetaDataDto meta = storage.getArtifactMetaData(groupId, artifactId);
        if (meta.getLabels() == null) {
            return null;
        }
        String key = ContractLabels.key(contractId, ContractLabels.SUFFIX_COMPATIBILITY_GROUP);
        return meta.getLabels().get(key);
    }

    public void setCompatibilityGroup(String groupId, String artifactId,
            String contractId, String compatGroup) {
        String key = ContractLabels.key(contractId, ContractLabels.SUFFIX_COMPATIBILITY_GROUP);
        Map<String, String> labels = Map.of(key, compatGroup);
        storage.mergeArtifactLabels(groupId, artifactId, key, labels);
    }

    /**
     * Finds the compatibility group label ({@code contract.{contractId}.compatibility.group}) in the
     * given labels, as stored (the storage may normalize the case of label keys).
     */
    public static Optional<Map.Entry<String, String>> findCompatibilityGroup(Map<String, String> labels) {
        if (labels == null) {
            return Optional.empty();
        }
        String suffix = "." + ContractLabels.SUFFIX_COMPATIBILITY_GROUP;
        return labels.entrySet().stream()
                .filter(e -> e.getKey().startsWith(ContractLabels.PREFIX) && e.getKey().endsWith(suffix))
                .filter(e -> e.getValue() != null && !e.getValue().isBlank())
                .findFirst();
    }
}
