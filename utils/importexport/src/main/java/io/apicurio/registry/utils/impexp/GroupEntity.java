package io.apicurio.registry.utils.impexp;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.Map;

import static lombok.AccessLevel.PRIVATE;

@Builder
@NoArgsConstructor
@AllArgsConstructor(access = PRIVATE)
@ToString
@RegisterForReflection
public class GroupEntity extends Entity {

    public String groupId;
    public String description;
    public String artifactsType;
    public String owner;
    public long createdOn;
    public String modifiedBy;
    public long modifiedOn;
    public Map<String, String> labels;

    /**
     * @see io.apicurio.registry.utils.impexp.Entity#getEntityType()
     */
    @Override
    public EntityType getEntityType() {
        return EntityType.Group;
    }

}
