package io.apicurio.authz;

import java.util.Set;

import io.kroxylicious.authorizer.service.ResourceType;

/**
 * Operations on a Registry group, the {@code Group} resource type of access rules. A top-level
 * enum, so rules files can import it ({@code from io.apicurio.authz import Group;}).
 * {@code ADMIN} implies {@code WRITE}, which implies {@code READ}.
 */
public enum Group implements ResourceType<Group> {
    READ, WRITE, ADMIN;

    @Override
    public Set<Group> implies() {
        return switch (this) {
            case ADMIN -> Set.of(WRITE, READ);
            case WRITE -> Set.of(READ);
            case READ -> Set.of();
        };
    }
}
