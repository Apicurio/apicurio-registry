package io.apicurio.registry.storage;

import io.apicurio.registry.storage.model.Artifact;
import io.apicurio.registry.storage.model.ArtifactId;

import java.util.List;

/**
 * Storage object that grants access to all artifacts without specifying the sequence first,
 * using a global numeric artifact ID.
 *
 * Injectable
 */
public interface ArtifactStorage extends ROKeyValueStorage<Long, Artifact> {

}
