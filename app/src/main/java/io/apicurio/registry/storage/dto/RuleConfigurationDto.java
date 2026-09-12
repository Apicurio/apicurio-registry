package io.apicurio.registry.storage.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.*;

/**
 * Data transfer object representing the configuration of a content rule. A rule defines a validation
 * constraint (validity, compatibility, or integrity) that is applied when content is added to the registry.
 * The configuration specifies the level or mode of the rule, and the failure action controls whether a
 * violation rejects the operation or is recorded without rejecting it.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode
@ToString
@RegisterForReflection
public class RuleConfigurationDto {

    private String configuration; // TODO why not a map?

    @Builder.Default
    private RuleAction onFailure = RuleAction.ERROR;

    public RuleConfigurationDto(String configuration) {
        this.configuration = configuration;
    }

    public RuleAction getOnFailure() {
        return onFailure == RuleAction.NONE ? RuleAction.NONE : RuleAction.ERROR;
    }

    public static RuleAction parseOnFailure(String onFailure) {
        return RuleAction.NONE.name().equals(onFailure) ? RuleAction.NONE : RuleAction.ERROR;
    }
}
