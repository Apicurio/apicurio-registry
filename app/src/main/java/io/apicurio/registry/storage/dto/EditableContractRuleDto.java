package io.apicurio.registry.storage.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.*;

import java.util.Map;
import java.util.Set;

/**
 * Data transfer object representing the user-editable subset of a contract rule. This includes fields that
 * can be modified when creating or updating a rule.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode
@ToString
@RegisterForReflection
public class EditableContractRuleDto {
    private String name;
    private RuleKind kind;
    private String type;
    private RuleMode mode;
    private String expr;
    private Map<String, String> params;
    private Set<String> tags;
    private RuleAction onSuccess;
    private RuleAction onFailure;
    private boolean disabled;
}
