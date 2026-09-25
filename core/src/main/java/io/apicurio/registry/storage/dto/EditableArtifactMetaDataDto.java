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

/**
 * Data transfer object representing the user-editable subset of artifact metadata. This includes fields that
 * can be modified after artifact creation, such as name, description, labels, and owner.
 */
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
