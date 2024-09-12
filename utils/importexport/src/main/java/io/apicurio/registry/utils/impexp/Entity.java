package io.apicurio.registry.utils.impexp;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public abstract class Entity {

    @JsonIgnore
    public abstract EntityType getEntityType();

}
