package io.apicurio.registry.storage.dto;

import io.apicurio.registry.types.VersionState;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Map;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode
@ToString
@RegisterForReflection
public class EditableVersionMetaDataDto {

    public static EditableVersionMetaDataDto fromEditableArtifactMetaDataDto(
            EditableArtifactMetaDataDto amd) {
        return EditableVersionMetaDataDto.builder().name(amd.getName()).description(amd.getDescription())
                .labels(amd.getLabels()).build();
    }

    private String name;
    private String description;
    private VersionState state;
    private Map<String, String> labels;
}
