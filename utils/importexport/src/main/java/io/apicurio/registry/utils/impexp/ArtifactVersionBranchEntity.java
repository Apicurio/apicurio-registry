package io.apicurio.registry.utils.impexp;

import io.apicurio.registry.model.BranchId;
import io.apicurio.registry.model.GAV;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.ToString;

import static lombok.AccessLevel.PRIVATE;

@Builder
@NoArgsConstructor
@AllArgsConstructor(access = PRIVATE)
@ToString
@RegisterForReflection
public class ArtifactVersionBranchEntity extends Entity {

    public String groupId;
    public String artifactId;
    public String version;
    public String branch;
    public int branchOrder;


    public GAV toGAV() {
        return new GAV(groupId, artifactId, version);
    }


    public BranchId toBranchId() {
        return new BranchId(branch);
    }


    @Override
    public EntityType getEntityType() {
        return EntityType.ArtifactVersionBranch;
    }
}
