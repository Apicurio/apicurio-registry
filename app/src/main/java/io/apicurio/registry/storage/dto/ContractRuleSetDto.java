package io.apicurio.registry.storage.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.*;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode
@ToString
@RegisterForReflection
public class ContractRuleSetDto {
    private List<ContractRuleDto> domainRules;
    private List<ContractRuleDto> migrationRules;
}
