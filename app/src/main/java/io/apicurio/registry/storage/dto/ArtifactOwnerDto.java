package io.apicurio.registry.storage.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode
@ToString
@RegisterForReflection
public class ArtifactOwnerDto {

    private String owner;

    /**
     * @return the owner
     */
    public String getOwner() {
        return owner;
    }

    /**
     * @param Owner the owner to set
     */
    public void setOwner(String owner) {
        this.owner = owner;
    }

}
