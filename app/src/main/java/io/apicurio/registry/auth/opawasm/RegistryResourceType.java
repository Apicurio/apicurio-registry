package io.apicurio.registry.auth.opawasm;

import java.util.Set;

import io.kroxylicious.authorizer.service.ResourceType;

public class RegistryResourceType {

    public enum Artifact implements ResourceType<Artifact> {
        Read, Write, Admin;

        @Override
        public Set<Artifact> implies() {
            return switch (this) {
                case Admin -> Set.of(Write, Read);
                case Write -> Set.of(Read);
                case Read -> Set.of();
            };
        }
    }

    public enum Group implements ResourceType<Group> {
        Read, Write, Admin;

        @Override
        public Set<Group> implies() {
            return switch (this) {
                case Admin -> Set.of(Write, Read);
                case Write -> Set.of(Read);
                case Read -> Set.of();
            };
        }
    }
}
