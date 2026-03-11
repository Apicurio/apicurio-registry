package io.apicurio.registry.storage.impl.polling;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.impl.gitops.model.Any;
import io.apicurio.registry.storage.impl.gitops.model.Type;

import java.util.Optional;

/**
 * Interface representing a data file that can be processed by the polling-based registry storage.
 * This abstraction allows different data sources (Git, Kubernetes ConfigMaps, etc.) to be used
 * with the same processing logic.
 */
public interface DataFile {

    /**
     * Returns the path of this data file. The path is used for indexing and file reference resolution.
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
    default boolean isType(Type type) {
        return getAny().map(a -> type == a.getType()).orElse(false);
    }

    /**
     * Returns the entity contained in this file, cast to the expected type.
     * This method assumes the caller knows the correct type.
     */
    @SuppressWarnings("unchecked")
    default <T> T getEntityUnchecked() {
        return (T) getAny().get().getEntity();
    }
}
