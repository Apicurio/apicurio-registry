package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.storage.RegistryStorage;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class KafkaSqlSnapshotSchedulerTest {

    private KafkaSqlSnapshotScheduler newScheduler(RegistryStorage storage) {
        KafkaSqlSnapshotScheduler scheduler = new KafkaSqlSnapshotScheduler();
        scheduler.log = LoggerFactory.getLogger(KafkaSqlSnapshotScheduler.class);
        scheduler.storage = storage;
        return scheduler;
    }

    @Test
    public void testTriggersSnapshotWhenReadyAndWritable() {
        RegistryStorage storage = mock(RegistryStorage.class);
        when(storage.isReady()).thenReturn(true);
        when(storage.isReadOnly()).thenReturn(false);

        newScheduler(storage).run();

        verify(storage).triggerSnapshotCreation();
    }

    @Test
    public void testSkipsWhenStorageNotReady() {
        RegistryStorage storage = mock(RegistryStorage.class);
        when(storage.isReady()).thenReturn(false);

        newScheduler(storage).run();

        verify(storage, never()).triggerSnapshotCreation();
    }

    @Test
    public void testSkipsWhenStorageIsReadOnly() {
        RegistryStorage storage = mock(RegistryStorage.class);
        when(storage.isReady()).thenReturn(true);
        when(storage.isReadOnly()).thenReturn(true);

        newScheduler(storage).run();

        verify(storage, never()).triggerSnapshotCreation();
    }

    @Test
    public void testExceptionDuringSnapshotCreationIsHandled() {
        RegistryStorage storage = mock(RegistryStorage.class);
        when(storage.isReady()).thenReturn(true);
        when(storage.isReadOnly()).thenReturn(false);
        when(storage.triggerSnapshotCreation()).thenThrow(new RuntimeException("boom"));

        assertDoesNotThrow(() -> newScheduler(storage).run());
    }
}
