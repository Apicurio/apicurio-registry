package io.apicurio.registry.auth.resourcebased;

import java.util.Set;

import io.kroxylicious.authorizer.service.ResourceType;

public enum RegistryArtifact implements ResourceType<RegistryArtifact> {

    Read,
    Write,
    Admin;

    @Override
    public Set<RegistryArtifact> implies() {
        return switch (this) {
            case Admin -> Set.of(Write, Read);
            case Write -> Set.of(Read);
            case Read -> Set.of();
        };
    }
}
