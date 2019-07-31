package io.apicurio.registry.storage;

import io.apicurio.registry.storage.model.ArtifactSequenceId;

import java.util.Set;

/**
 * Stores Artifact Sequences and allows access to related data.
 * <p>
 * Injectable.
 */
public interface ArtifactSequenceStorage extends ROKeyValueStorage<ArtifactSequenceId, ArtifactSequence> {

    /**
     * Create a new sequence without specifying the sequence name explicitly.
     * Implementation MUST generate an unique cluster-wide ID number.
     *
     * @return the new sequence
     */
    ArtifactSequence create();

    /**
     * Create a new sequence by specifying the sequence name explicitly.
     * If the sequence already exists, an exception is thrown.
     *
     * @param sequenceId new sequence name
     * @return the new sequence
     * @throws io.apicurio.registry.storage.StorageException if the sequence already exists or the operation fails
     */
    ArtifactSequence create(String sequenceId);

    /**
     * Get a set of keys (IDs) for all known sequences.
     *
     * @return the set of all keys
     */
    Set<ArtifactSequenceId> getAllKeys();

    /**
     * Get a storage object for managing rule configuration for the specified sequence.
     *
     * @param id sequence ID
     * @return the rule instance storage
     */
    RuleInstanceStorage getRuleStorage(ArtifactSequenceId id);

    /**
     * Get an object for managing meta data for the specified sequence.
     *
     * @param id sequence ID
     * @return the meta data object
     */
    MetaData getMetaDataStorage(ArtifactSequenceId id);

    void delete(ArtifactSequenceId key);
}
