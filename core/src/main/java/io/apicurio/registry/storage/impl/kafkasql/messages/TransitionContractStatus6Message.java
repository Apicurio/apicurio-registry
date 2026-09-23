package io.apicurio.registry.storage.impl.kafkasql.messages;

import io.apicurio.registry.storage.RegistryStorage;
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
public class TransitionContractStatus6Message extends AbstractMessage {

    private String groupId;
    private String artifactId;
    private String fromStatus;
    private String toStatus;
    private String prefix;
    private String effectiveDate;

    @Override
    public Object dispatchTo(RegistryStorage storage) {
        storage.transitionContractStatus(groupId, artifactId, fromStatus, toStatus, prefix,
                effectiveDate);
        return null;
    }
}
