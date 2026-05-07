package io.apicurio.authz;

import java.util.Set;

public interface ResourceType<S extends Enum<S> & ResourceType<S>> {

    default Set<S> implies() {
        return Set.of();
    }
}
