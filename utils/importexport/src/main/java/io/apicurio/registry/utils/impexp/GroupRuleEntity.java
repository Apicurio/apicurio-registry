package io.apicurio.registry.utils.impexp;

import io.apicurio.registry.types.RuleType;
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
public class GroupRuleEntity extends Entity {

    public String groupId;
    public RuleType type;
    public String configuration;

    /**
     * @see Entity#getEntityType()
     */
    @Override
    public EntityType getEntityType() {
        return EntityType.GroupRule;
    }
}
