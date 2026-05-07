package io.apicurio.authz;

import java.util.Objects;

public record Action(ResourceType<?> operation, String resourceName) {

    public Action {
        Objects.requireNonNull(operation);
        Objects.requireNonNull(resourceName);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public Class<? extends ResourceType<?>> resourceTypeClass() {
        return (Class) operation.getClass();
    }
}
