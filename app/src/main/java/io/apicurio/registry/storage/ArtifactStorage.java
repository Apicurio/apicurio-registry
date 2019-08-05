package io.apicurio.registry.storage;

import io.apicurio.registry.storage.model.ArtifactId;

import java.util.Set;

/**
 * Stores <em>artifacts</em>, sequences of {@link io.apicurio.registry.storage.model.ArtifactVersion}
 * and allows access to related data for each <em>artifact</em>.
 * <p>
 * Injectable.
 */
public interface ArtifactStorage extends ROKeyValueStorage<ArtifactId, ArtifactVersionStorage> {

    /**
     * Create a new <em>artifact</em> (i.e. a sequence of {@link io.apicurio.registry.storage.model.ArtifactVersion})
     * without specifying the <em>artifact ID</em> explicitly.
     * <p>
     * Implementation MUST generate an unique cluster-wide ID.
     */
    ArtifactVersionStorage create();

    /**
     * Create a new <em>artifact</em> (i.e. a sequence of {@link io.apicurio.registry.storage.model.ArtifactVersion})
     * by specifying the <em>artifact ID</em> explicitly.
     * <p>
     * If the <em>artifact</em> already exists, an exception is thrown.
     */
    ArtifactVersionStorage create(String artifactId);

    /**
     * Get a set of {@link io.apicurio.registry.storage.model.ArtifactId} (keys) for all known <em>artifacts</em>.
     */
    Set<ArtifactId> getAllKeys();

    /**
     * Get a storage to manage <em>rules</em> for the specified <em>artifact</em>.
     */
    RuleInstanceStorage getRuleInstanceStorage(ArtifactId id);

    /**
     * Get a storage to manage <em>metadata</em> for the specified <em>artifact</em>.
     */
    MetaDataStorage getMetaDataStorage(ArtifactId id);

    /**
     * Delete the <em>artifact</em>.
     * <p>
     * This also removes every associated {@link io.apicurio.registry.storage.model.ArtifactVersion}.
     */
    void delete(ArtifactId key);
}
