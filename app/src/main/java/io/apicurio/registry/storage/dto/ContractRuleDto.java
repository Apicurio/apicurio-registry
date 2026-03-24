package io.apicurio.registry.storage.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.*;

import java.util.Map;
import java.util.Set;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode
@ToString
@RegisterForReflection
public class ContractRuleDto {
    private String name;
    private RuleKind kind;
    private String type;  // CEL, CEL_FIELD, ENCRYPT, DECRYPT, JSONATA, etc.
    private RuleMode mode;
    private String expr;
    private Map<String, String> params;
    private Set<String> tags;
    private RuleAction onSuccess;
    private RuleAction onFailure;
    private boolean disabled;
    private int orderIndex;
}