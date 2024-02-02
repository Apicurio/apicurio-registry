package io.apicurio.registry.storage.dto;

import io.apicurio.registry.types.ArtifactState;
import lombok.*;

import java.util.*;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class SearchedVersionDto {

    private String name;
    private String description;
    private Date createdOn;
    private String createdBy;
    private String type;
    private ArtifactState state;
    private long globalId;
    private long contentId;
    private String version;
    private int versionOrder;
}
