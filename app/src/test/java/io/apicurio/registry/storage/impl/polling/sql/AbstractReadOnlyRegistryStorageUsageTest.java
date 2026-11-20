package io.apicurio.registry.storage.impl.polling.sql;

import io.apicurio.registry.storage.dto.SchemaUsageEventDto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * The usage flush runs against read-only storages (GitOps, KubernetesOps) too. It must be harmless there,
 * not log an {@code UnreachableCodeException} on every cycle. The retention job never reaches a read-only
 * storage because {@code UsageAggregationJob} checks {@code isReadOnly()} first, so only the flush path
 * threw before this change.
 */
class AbstractReadOnlyRegistryStorageUsageTest {

    private final AbstractReadOnlyRegistryStorage storage = mock(AbstractReadOnlyRegistryStorage.class,
            CALLS_REAL_METHODS);

    @Test
    void recordUsageEventIsIgnored() {
        assertDoesNotThrow(() -> storage.recordUsageEvent(SchemaUsageEventDto.builder().globalId(1)
                .clientId("client").operation("SERIALIZE").eventTimestamp(1L).build()));
        verify(storage, never()).readOnlyViolation();
    }

    @Test
    void deleteOldUsageEventsIsIgnored() {
        assertDoesNotThrow(() -> storage.deleteOldUsageEvents(1L));
        verify(storage, never()).readOnlyViolation();
    }
}
