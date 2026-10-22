package io.apicurio.registry.storage.impl.kafkasql.messages;

import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.impl.kafkasql.AbstractMessage;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@EqualsAndHashCode(callSuper = false)
@ToString
public class DeleteGlobalContractRuleset0Message extends AbstractMessage {

    /**
     * @see io.apicurio.registry.storage.impl.kafkasql.KafkaSqlMessage#dispatchTo(io.apicurio.registry.storage.RegistryStorage)
     */
    @Override
    public Object dispatchTo(RegistryStorage storage) {
        storage.deleteGlobalContractRuleset();
        return null;
    }
}
