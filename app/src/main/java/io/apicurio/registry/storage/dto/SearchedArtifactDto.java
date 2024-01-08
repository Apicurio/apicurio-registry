package io.apicurio.registry.storage.dto;

import io.apicurio.registry.types.ArtifactState;
import lombok.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class SearchedArtifactDto {

    private String groupId;
    private String id;
    private String name;
    private String description;
    private Date createdOn;
    private String createdBy;
    private String type;
    private List<String> labels = new ArrayList<String>();
    private ArtifactState state;
    private Date modifiedOn;
    private String modifiedBy;
}
