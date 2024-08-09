package io.apicurio.registry.utils.impexp.v3;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.apicurio.registry.utils.impexp.Entity;
import io.apicurio.registry.utils.impexp.EntityType;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.ToString;

import static lombok.AccessLevel.PRIVATE;

@Builder
@NoArgsConstructor
@AllArgsConstructor(access = PRIVATE)
@ToString
@RegisterForReflection
public class ContentEntity extends Entity {

    public long contentId;
    public String canonicalHash;
    public String contentHash;
    public String artifactType;
    public String contentType;

    @JsonIgnore
    @ToString.Exclude
    public byte[] contentBytes;

    public String serializedReferences;

    /**
     * @see Entity#getEntityType()
     */
    @Override
    public EntityType getEntityType() {
        return EntityType.Content;
    }

}
