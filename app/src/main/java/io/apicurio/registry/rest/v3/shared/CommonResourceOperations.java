package io.apicurio.registry.rest.v3.shared;

import io.apicurio.registry.rest.v3.V3ApiUtil;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.types.Current;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class CommonResourceOperations {

    @Inject
    @Current
    RegistryStorage storage;

    public List<ArtifactReference> getReferencesByContentHash(String contentHash) {
        ContentWrapperDto artifact = storage.getContentByHash(contentHash);
        return artifact.getReferences().stream().map(V3ApiUtil::referenceDtoToReference)
                .collect(Collectors.toList());
    }
}
