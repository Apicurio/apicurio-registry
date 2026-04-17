package io.apicurio.registry.utils.impexp.v3;

import io.apicurio.registry.utils.impexp.Entity;
import io.apicurio.registry.utils.impexp.EntityType;
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
public class ContractRuleEntity extends Entity {

    public String groupId;
    public String artifactId;
    public Long globalId;
    public String ruleCategory;
    public int orderIndex;
    public String ruleName;
    public String kind;
    public String ruleType;
    public String mode;
    public String expr;
    public String params;
    public String tags;
    public String onSuccess;
    public String onFailure;
    public boolean disabled;

    /**
     * @see Entity#getEntityType()
     */
    @Override
    public EntityType getEntityType() {
        return EntityType.ContractRule;
    }
}
