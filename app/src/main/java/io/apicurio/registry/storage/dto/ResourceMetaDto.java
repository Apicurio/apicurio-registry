package io.apicurio.registry.storage.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode
@ToString
@RegisterForReflection
public class ResourceMetaDto {

    private String groupId;
    private String artifactId;
    private String compatibility;
    private String compatibilityAuthority;
    private String defaultVersionId;
    private boolean defaultVersionSticky;
    private boolean readonly;
    private String xref;
    private DeprecationInfoDto deprecated;
    private long epoch;
}
