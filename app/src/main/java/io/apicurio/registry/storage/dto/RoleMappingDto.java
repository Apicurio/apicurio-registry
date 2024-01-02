package io.apicurio.registry.storage.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@AllArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode
@ToString
@RegisterForReflection
public class RoleMappingDto {

    private String principalId;
    private String role;
    private String principalName;

    /**
     * Constructor.
     */
    public RoleMappingDto() {
    }

}
