package io.apicurio.authz;

import java.util.Set;

import io.kroxylicious.authorizer.service.ResourceType;

/**
 * Operations on a Registry artifact, the {@code Artifact} resource type of access rules. A top-level
 * enum, so rules files can import it ({@code from io.apicurio.authz import Artifact;}).
 * {@code ADMIN} implies {@code WRITE}, which implies {@code READ}.
 */
public enum Artifact implements ResourceType<Artifact> {
    READ, WRITE, ADMIN;

    @Override
    public Set<Artifact> implies() {
        return switch (this) {
            case ADMIN -> Set.of(WRITE, READ);
            case WRITE -> Set.of(READ);
            case READ -> Set.of();
        };
    }
}
