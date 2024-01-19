package io.apicurio.registry.storage.dto;

import io.apicurio.registry.model.GAV;
import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class ArtifactBranchDto {

    private String groupId;

    private String artifactId;

    private String branchId;

    private int branchOrder;

    private String version;


    public GAV toGAV() {
        return new GAV(groupId, artifactId, version);
    }
}
