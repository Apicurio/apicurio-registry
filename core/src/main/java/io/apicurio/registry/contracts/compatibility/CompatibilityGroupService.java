package io.apicurio.registry.contracts.compatibility;

import io.apicurio.registry.contracts.ContractLabels;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.cdi.Current;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;

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
}
