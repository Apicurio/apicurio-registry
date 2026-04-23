package io.apicurio.registry.auth.resourcebased;

import java.util.Set;

import io.kroxylicious.authorizer.service.ResourceType;

public enum RegistryGroup implements ResourceType<RegistryGroup> {

    Read,
    Write,
    Admin;

    @Override
    public Set<RegistryGroup> implies() {
        return switch (this) {
            case Admin -> Set.of(Write, Read);
            case Write -> Set.of(Read);
            case Read -> Set.of();
        };
    }
}
