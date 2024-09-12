package io.apicurio.registry.storage.dto;

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
public class EditableArtifactMetaDataDto {

    public static EditableArtifactMetaDataDto fromEditableVersionMetaDataDto(EditableVersionMetaDataDto vmd) {
        return EditableArtifactMetaDataDto.builder().name(vmd.getName()).description(vmd.getDescription())
                .labels(vmd.getLabels()).build();
    }

    private String name;
    private String description;
    private String owner;
    private Map<String, String> labels;
}
