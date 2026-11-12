package io.apicurio.registry.storage.impl.polling.model;

import io.apicurio.registry.storage.impl.polling.format.DataFormat;
import io.apicurio.registry.storage.impl.polling.model.v0.Artifact;
import io.apicurio.registry.storage.impl.polling.model.v0.Content;
import io.apicurio.registry.storage.impl.polling.model.v0.Group;
import io.apicurio.registry.storage.impl.polling.model.v0.Registry;
import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

/**
 * The kinds of metadata files. The {@code $type} of a file combines the kind with the data format
 * version, for example {@code artifact-v1}; {@link DataFormat} resolves it. The model classes are those
 * of the current version.
 */
@Getter
public enum Type {

    REGISTRY("registry", Registry.class), GROUP("group", Group.class), ARTIFACT("artifact",
            Artifact.class), CONTENT("content", Content.class);

    private final String kind;

    private final Class<?> klass;

    Type(String kind, Class<?> klass) {
        this.kind = kind;
        this.klass = klass;
    }

    /**
     * Returns the {@code $type} value of this kind in the current data format version.
     */
    public String getType() {
        return DataFormat.currentTypeName(this);
    }

    public static Optional<Type> fromKind(String kind) {
        return Arrays.stream(values()).filter(t -> t.kind.equals(kind)).findAny();
    }

    @Override
    public String toString() {
        return getType();
    }
}
