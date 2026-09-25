package io.apicurio.registry.storage.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.*;

/**
 * Data transfer object representing a mapping from a principal (user identity) to a role within the registry.
 * Role mappings are used for access control when role-based authorization is enabled.
 */
@NoArgsConstructor
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
}
