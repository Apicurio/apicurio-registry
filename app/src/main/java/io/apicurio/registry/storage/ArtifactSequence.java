package io.apicurio.registry.storage;

import io.apicurio.registry.storage.model.Artifact;
import io.apicurio.registry.storage.model.ArtifactId;
import io.apicurio.registry.storage.model.ArtifactSequenceId;

import java.util.List;

/**
 * Represents an Artifact Sequence and allows access to Atrifacts and related data.
 * <p>
 * This object should not be assumed to be lightweight or stateless, and TODO users should close it after use?
 * <p>
 * NOT Injectable. TODO via producer/qualifier?
 */
public interface ArtifactSequence extends KeyValueStorage<ArtifactId, Artifact> /*, Closeable */ {

    /**
     * Get the ID of the sequence this object represents.
     *
     * @return the sequence ID
     */
    ArtifactSequenceId getId();

    /**
     * Create/Add a new artifact to this sequence.
     *
     * @param value the artifact data
     * @return a computer generated ID
     */
    ArtifactId create(Artifact value);

    /**
     * Return a list of Artifact IDs representing all artifacts in this sequence.
     *
     * @return the list of IDs
     */
    List<ArtifactId> getAllKeys();

    /**
     * @return ID of the artifact that has been added most recently
     */
    ArtifactId getLatestVersion();

    /**
     * Get an object for managing meta data for the specified sequence.
     *
     * @param key Artifact ID
     * @return the meta data object
     */
    MetaData getMetadataStorage(ArtifactId key);
}
