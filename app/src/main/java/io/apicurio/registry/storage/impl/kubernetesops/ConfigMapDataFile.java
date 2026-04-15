package io.apicurio.registry.storage.impl.kubernetesops;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.impl.polling.AbstractPollingDataFile;
import io.apicurio.registry.storage.impl.polling.ProcessingState;
import io.apicurio.registry.storage.impl.polling.model.Any;
import lombok.ToString;
import java.nio.file.Path;

/**
 * Implementation of PollingDataFile for Kubernetes ConfigMap-based data.
 * Each ConfigMapDataFile represents a single data entry from a ConfigMap.
 */
@ToString(callSuper = true)
public class ConfigMapDataFile extends AbstractPollingDataFile {

    private static final String SOURCE_ID = "kubernetes";

    private ConfigMapDataFile(String path, ContentHandle data, ProcessingState state) {
        super(SOURCE_ID, path, data, Any.from(state, path, data));
    }

    /**
     * Creates a ConfigMapDataFile from a ConfigMap data entry.
     * <p>
     * The dataKey should contain the full relative path (e.g., "test/artifact-petstore.yaml")
     * to maintain compatibility with the relative path resolution logic used by GitOps.
     *
     * @param state the processing state for error recording
     * @param dataKey the key within the ConfigMap data, used as the file path
     * @param content the content of the data entry
     * @return a new ConfigMapDataFile instance
     */
    public static ConfigMapDataFile create(ProcessingState state, String dataKey, String content) {
        return new ConfigMapDataFile(Path.of(dataKey).normalize().toString(), ContentHandle.create(content), state);
    }
}
