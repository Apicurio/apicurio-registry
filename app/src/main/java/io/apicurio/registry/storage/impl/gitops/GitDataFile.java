package io.apicurio.registry.storage.impl.gitops;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.impl.polling.AbstractPollingDataFile;
import io.apicurio.registry.storage.impl.polling.ProcessingState;
import io.apicurio.registry.storage.impl.polling.model.Any;
import lombok.ToString;
import java.nio.file.Path;
import org.eclipse.jgit.lib.ObjectDatabase;
import org.eclipse.jgit.lib.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * Represents a file from a Git repository. Supports two modes:
 * <ul>
 * <li><b>Eager:</b> Content is read immediately (used for registry metadata files that need parsing).</li>
 * <li><b>Lazy:</b> Content is deferred until first access via {@link #getData()} (used for content/data files
 *     to avoid loading unnecessary files like READMEs, CI configs, etc. into memory).</li>
 * </ul>
 */
@ToString(callSuper = true, exclude = {"objectDatabase", "objectId"})
public class GitDataFile extends AbstractPollingDataFile {

    private static final Logger log = LoggerFactory.getLogger(GitDataFile.class);

    // Lazy loading references (set at creation for non-metadata files)
    private ObjectDatabase objectDatabase;
    private ObjectId objectId;
    private boolean loaded;

    private GitDataFile(String sourceId, String path, ContentHandle data, ProcessingState state) {
        super(sourceId, path, data, Any.from(state, path, data));
        this.loaded = true;
    }

    private GitDataFile(String sourceId, String path, ObjectDatabase objectDatabase, ObjectId objectId) {
        super(sourceId, path, null, Optional.empty());
        this.objectDatabase = objectDatabase;
        this.objectId = objectId;
        this.loaded = false;
    }

    /**
     * Creates a GitDataFile with eagerly loaded content. Used for registry metadata files
     * (*.registry.yaml/json) that need to be parsed immediately.
     */
    public static GitDataFile create(String sourceId, ProcessingState state, String path, InputStream stream) {
        return new GitDataFile(sourceId, Path.of(path).normalize().toString(), ContentHandle.create(stream), state);
    }

    /**
     * Creates a GitDataFile with lazily loaded content. The file bytes are not read until
     * {@link #getData()} is called. Used for non-metadata files (content files, READMEs, etc.)
     * to avoid loading unnecessary data into memory.
     */
    public static GitDataFile createLazy(String sourceId, String path, ObjectDatabase objectDatabase, ObjectId objectId) {
        return new GitDataFile(sourceId, Path.of(path).normalize().toString(), objectDatabase, objectId);
    }

    @Override
    public ContentHandle getData() {
        if (!loaded) {
            try (InputStream stream = objectDatabase.open(objectId).openStream()) {
                data = ContentHandle.create(stream);
            } catch (IOException e) {
                log.error("Failed to lazily load content for file {}", path, e);
                throw new RuntimeException("Failed to load content for file: " + path, e);
            } finally {
                loaded = true;
                objectDatabase = null;
                objectId = null;
            }
        }
        return data;
    }
}
