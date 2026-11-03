package io.apicurio.registry.storage.impl.kafkasql.messages;

import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.impl.kafkasql.AbstractMessage;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Journals orphaned content cleanup so that every replica deletes orphaned content at the same journal
 * position. See {@code KafkaSqlRegistryStorage#deleteAllOrphanedContent()}.
 */
@NoArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode(callSuper = false)
@ToString
public class DeleteAllOrphanedContent0Message extends AbstractMessage {

    @Override
    public Object dispatchTo(RegistryStorage storage) {
        storage.deleteAllOrphanedContent();
        return null;
    }

}
