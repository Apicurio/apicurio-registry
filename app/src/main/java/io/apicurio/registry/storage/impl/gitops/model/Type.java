package io.apicurio.registry.storage.impl.gitops.model;

import io.apicurio.registry.storage.impl.gitops.model.v0.Artifact;
import io.apicurio.registry.storage.impl.gitops.model.v0.Content;
import io.apicurio.registry.storage.impl.gitops.model.v0.Group;
import io.apicurio.registry.storage.impl.gitops.model.v0.Registry;
import lombok.Getter;
import lombok.ToString;

import java.util.Arrays;
import java.util.Optional;

@Getter
@ToString(onlyExplicitlyIncluded = true)
public enum Type {

    REGISTRY("registry-v0", Registry.class), GROUP("group-v0", Group.class), ARTIFACT("artifact-v0",
            Artifact.class), CONTENT("content-v0", Content.class);

    @ToString.Include
    private final String type;

    private final Class<?> klass;

    public static Optional<Type> from(String type) {
        return Arrays.stream(values()).filter(t -> t.type != null && t.type.equals(type)).findAny();
    }

    Type(String type, Class<?> klass) {
        this.type = type;
        this.klass = klass;
    }
}
