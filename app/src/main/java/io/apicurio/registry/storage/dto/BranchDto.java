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
public class BranchDto {

    private String groupId;

    private String artifactId;

    private String branch;

    private int branchOrder;

    private String version;


    public GAV toGAV() {
        return new GAV(groupId, artifactId, version);
    }
}
