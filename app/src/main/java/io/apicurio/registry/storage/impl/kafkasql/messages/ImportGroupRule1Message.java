package io.apicurio.registry.storage.impl.kafkasql.messages;

import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.impl.kafkasql.AbstractMessage;
import io.apicurio.registry.utils.impexp.GroupRuleEntity;
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
public class ImportGroupRule1Message extends AbstractMessage {

    private GroupRuleEntity entity;

    /**
     * @see io.apicurio.registry.storage.impl.kafkasql.KafkaSqlMessage#dispatchTo(RegistryStorage)
     */
    @Override
    public Object dispatchTo(RegistryStorage storage) {
        storage.importGroupRule(entity);
        return null;
    }

}
