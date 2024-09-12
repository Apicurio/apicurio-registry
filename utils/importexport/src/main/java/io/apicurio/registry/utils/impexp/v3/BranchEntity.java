package io.apicurio.registry.utils.impexp.v3;

import io.apicurio.registry.model.BranchId;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.utils.impexp.Entity;
import io.apicurio.registry.utils.impexp.EntityType;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;

import static lombok.AccessLevel.PRIVATE;

@Builder
@NoArgsConstructor
@AllArgsConstructor(access = PRIVATE)
@ToString
@RegisterForReflection
public class BranchEntity extends Entity {

    public String groupId;
    public String artifactId;
    public String branchId;
    public String description;
    public boolean systemDefined;
    public String owner;
    public long createdOn;
    public String modifiedBy;
    public long modifiedOn;
    public List<String> versions;

    public GA toGA() {
        return new GA(groupId, artifactId);
    }

    public BranchId toBranchId() {
        return new BranchId(branchId);
    }

    @Override
    public EntityType getEntityType() {
        return EntityType.Branch;
    }
}
