package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.storage.RegistryStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;

import java.util.stream.Stream;

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

    private static Stream<Arguments> skipScenarios() {
        return Stream.of(Arguments.of("storage not ready", false, false),
                Arguments.of("storage read-only", true, true));
    }

    @ParameterizedTest(name = "skips snapshot creation when {0}")
    @MethodSource("skipScenarios")
    public void testSkipsWhenGuardConditionFails(String scenario, boolean ready, boolean readOnly) {
        RegistryStorage storage = mock(RegistryStorage.class);
        when(storage.isReady()).thenReturn(ready);
        when(storage.isReadOnly()).thenReturn(readOnly);

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
