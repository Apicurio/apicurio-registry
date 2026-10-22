package io.apicurio.registry.extensions;

import io.apicurio.registry.model.GA;
import io.apicurio.registry.storage.RegistryStorage;

/**
 * Describes an artifact version write for {@link ArtifactVersionWriteHook} implementations.
 */
public final class VersionWriteContext {

    /**
     * The kind of write being performed.
     */
    public enum Operation {
        /** A new artifact is created together with its first version. */
        CREATE_ARTIFACT,
        /** A new version is added to an existing artifact. */
        CREATE_VERSION,
        /** A DRAFT version transitions to a published state. */
        PUBLISH_DRAFT
    }

    private final Operation operation;
    private final RegistryStorage storage;
    private final GA ga;
    private final String artifactType;
    private final String owner;

    /**
     * @param operation    the kind of write
     * @param storage      the storage the write goes to; hooks that register or update related
     *                     artifacts must use this instance
     * @param ga           the artifact being written to. Not a {@code GAV}: on create the version is often
     *                     assigned by storage only after the hooks have run
     * @param artifactType the artifact type
     * @param owner        the principal performing the write
     */
    public VersionWriteContext(Operation operation, RegistryStorage storage, GA ga, String artifactType,
            String owner) {
        this.operation = operation;
        this.storage = storage;
        this.ga = ga;
        this.artifactType = artifactType;
        this.owner = owner;
    }

    public Operation getOperation() {
        return operation;
    }

    /**
     * @return {@code true} unless the write creates a new artifact
     */
    public boolean isUpdate() {
        return operation != Operation.CREATE_ARTIFACT;
    }

    public RegistryStorage getStorage() {
        return storage;
    }

    public GA getGa() {
        return ga;
    }

    public String getArtifactType() {
        return artifactType;
    }

    public String getOwner() {
        return owner;
    }
}
