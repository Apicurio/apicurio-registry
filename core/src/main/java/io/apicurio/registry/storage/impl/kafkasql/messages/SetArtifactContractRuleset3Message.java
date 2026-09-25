package io.apicurio.registry.storage.impl.kafkasql.messages;

import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ContractRuleSetDto;
import io.apicurio.registry.storage.impl.kafkasql.AbstractMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode(callSuper = false)
@ToString
public class SetArtifactContractRuleset3Message extends AbstractMessage {

    private String groupId;
    private String artifactId;
    private ContractRuleSetDto ruleset;

    @Override
    public Object dispatchTo(RegistryStorage storage) {
        storage.setArtifactContractRuleset(groupId, artifactId, ruleset);
        return null;
    }
}
