package io.apicurio.registry.storage;

import io.apicurio.registry.storage.model.ArtifactId;
import io.apicurio.registry.storage.model.ArtifactVersion;
import io.apicurio.registry.storage.model.ArtifactVersionId;

import java.util.List;

/**
 * Represents an <em>artifact</em>, a sequence of {@link io.apicurio.registry.storage.model.ArtifactVersion},
 * and allows access to related data for each <em>artifact version</em>.
 * <p>
 * NOT Injectable, get via {@link io.apicurio.registry.storage.ArtifactStorage}.
 */
public interface ArtifactVersionStorage extends KeyValueStorage<ArtifactVersionId, ArtifactVersion> {

    /**
     * Get the <em>artifact ID</em>ID of this sequence of {@link io.apicurio.registry.storage.model.ArtifactVersion}.
     */
    ArtifactId getArtifactId();

    /**
     * Create and add a new {@link io.apicurio.registry.storage.model.ArtifactVersion} to this <em>artifact<em>.
     *
     * @return the value that has been added, including generated data (IDs)
     */
    ArtifactVersion create(ArtifactVersion value);

    /**
     * Get the list of IDs for every <em>artifact version</em> in this artifact.
     */
    List<ArtifactVersionId> getAllKeys();

    /**
     * Get the latest <em>artifact version</em>, i.e. the {@link io.apicurio.registry.storage.model.ArtifactVersion}
     * with the greatest numeric version value.
     *
     * @return null if there is not yet any artifact version
     */
    ArtifactVersion getLatestVersion();

    /**
     * Get a storage to manage <em>metadata</em> for the specified <em>artifact version</em>.
     */
    MetaDataStorage getMetadataStorage(ArtifactVersionId key);
}
