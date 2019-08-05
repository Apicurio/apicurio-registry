package io.apicurio.registry.storage;

import io.apicurio.registry.storage.model.ArtifactVersion;

/**
 * Storage object that grants access to all {@link io.apicurio.registry.storage.model.ArtifactVersion}
 * using a global numeric artifact ID - {@link io.apicurio.registry.storage.model.ArtifactVersion#getGlobalId()}.
 * <p>
 * Injectable.
 */
public interface GlobalArtifactStorage extends ROKeyValueStorage<Long, ArtifactVersion> {

}
