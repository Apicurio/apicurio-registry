package io.apicurio.registry.storage.impl.kubernetesops;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.impl.gitops.model.Any;
import io.apicurio.registry.storage.impl.gitops.model.Type;
import io.apicurio.registry.storage.impl.polling.DataFile;
import io.apicurio.registry.storage.impl.polling.DataFileProcessingState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.commons.io.FilenameUtils;

import java.util.Optional;

import static lombok.AccessLevel.PRIVATE;

/**
 * Implementation of DataFile for Kubernetes ConfigMap-based data.
 * Each ConfigMapDataFile represents a single data entry from a ConfigMap.
 */
@Builder
@AllArgsConstructor(access = PRIVATE)
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class ConfigMapDataFile implements DataFile {

    /**
     * The path uniquely identifies this file within the registry configuration.
     * The path is the ConfigMap data key, which should be a relative path
     * (e.g., "test/artifact-petstore.yaml") to support relative path resolution.
     */
    @EqualsAndHashCode.Include
    private String path;

    /**
     * The content of this data file.
     */
    private ContentHandle data;

    /**
     * The parsed entity if the content is a valid entity definition.
     */
    private Optional<Any> any;

    /**
     * Whether this file has been processed.
     */
    @Setter
    private boolean processed;

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
    public static ConfigMapDataFile create(DataFileProcessingState state, String dataKey, String content) {

        String path = FilenameUtils.normalize(dataKey);
        ContentHandle data = ContentHandle.create(content);

        return ConfigMapDataFile.builder()
                .path(path)
                .data(data)
                .any(Any.from(state, path, data))
                .processed(false)
                .build();
    }

    @Override
    public boolean isType(Type type) {
        return any.map(a -> type == a.getType()).orElse(false);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getEntityUnchecked() {
        return (T) any.get().getEntity();
    }
}
