package io.apicurio.registry.storage;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * CDI event fired by the storage implementation.
 * Differs from {@see io.apicurio.registry.storage.impl.sql.SqlStorageEvent} because
 * this event is fired by non-SQL implementations as well.
 *
 */
@Builder
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class StorageEvent {

    private StorageEventType type;
}
