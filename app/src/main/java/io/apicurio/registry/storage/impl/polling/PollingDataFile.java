package io.apicurio.registry.storage.impl.polling;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.impl.polling.model.Any;
import io.apicurio.registry.storage.impl.polling.model.Type;

import java.util.Optional;

/**
 * Interface representing a data file that can be processed by the polling-based registry storage.
 * This abstraction allows different data sources (Git, Kubernetes ConfigMaps, etc.) to be used
 * with the same processing logic.
 */
public interface PollingDataFile {

    /**
     * Returns the identifier of the source that provided this file.
     * For GitOps, this is the repository ID. For KubernetesOps, a fixed value.
     */
    String getSourceId();

    /**
     * Returns the logical path of this data file within the data source.
     * This is not a filesystem path — it is a data-source-specific identifier
     * (e.g., a Git tree path or a Kubernetes ConfigMap data key).
     * Used for indexing, relative path resolution between files, and file extension detection.
     */
    String getPath();

    /**
     * Returns the content of this data file.
     */
    ContentHandle getData();

    /**
     * Returns the parsed entity if the file contains a valid entity definition.
     */
    Optional<Any> getAny();

    /**
     * Returns whether this file has been processed.
     */
    boolean isProcessed();

    /**
     * Marks this file as processed or unprocessed.
     */
    void setProcessed(boolean processed);

    /**
     * Checks if this file represents an entity of the given type.
     */
    boolean isType(Type type);

    /**
     * Returns the entity contained in this file, cast to the expected type.
     * This method assumes the caller knows the correct type.
     */
    <T> T getEntityUnchecked();
}
