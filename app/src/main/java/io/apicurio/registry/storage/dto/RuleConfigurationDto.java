package io.apicurio.registry.storage.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.*;

/**
 * Data transfer object representing the configuration of a content rule. A rule defines a validation
 * constraint (validity, compatibility, or integrity) that is applied when content is added to the registry.
 * The configuration specifies the level or mode of the rule.
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
}
