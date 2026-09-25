package io.apicurio.registry.rest.impl.shared;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Shared resource operations used by both v2 and v3 REST APIs.
 * This class centralizes common functionality that was previously duplicated.
 */
@ApplicationScoped
public class CommonResourceOperations {

    @Inject
    @Current
    RegistryStorage storage;

    /**
     * Gets artifact references by content hash and transforms them using the provided transformer function.
     *
     * @param contentHash the content hash to look up
     * @param transformer the function to transform each ArtifactReferenceDto to the desired type
     * @param <R> the target reference type (v2 or v3 ArtifactReference)
     * @return list of transformed artifact references
     */
    public <R> List<R> getReferencesByContentHash(String contentHash,
            Function<ArtifactReferenceDto, R> transformer) {
        ContentWrapperDto artifact = storage.getContentByHash(contentHash);
        return artifact.getReferences().stream()
                .map(transformer)
                .collect(Collectors.toList());
    }
}
