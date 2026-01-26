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

/**
 * DEPRECATED: This message is no longer generated but is kept for backward compatibility
 * with existing Kafka logs. Content hashes are now immutable and generated at content
 * creation time, stored in the content_hashes table instead of being updated.
 *
 * When replaying old Kafka logs that contain this message, it will update the canonical
 * hash entry in the content_hashes table to maintain data consistency during migration.
 */
@Deprecated
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode(callSuper = false)
@ToString
public class UpdateContentCanonicalHash3Message extends AbstractMessage {

    private String newCanonicalHash;
    private long contentId;
    private String contentHash;

    /**
     * @see io.apicurio.registry.storage.impl.kafkasql.KafkaSqlMessage#dispatchTo(io.apicurio.registry.storage.RegistryStorage)
     */
    @Override
    public Object dispatchTo(RegistryStorage storage) {
        // For backward compatibility with old Kafka logs, update the canonical hash
        // in the content_hashes table (old implementation updated content.canonicalHash column)
        storage.updateContentCanonicalHash(newCanonicalHash, contentId, contentHash);
        return null;
    }

}
