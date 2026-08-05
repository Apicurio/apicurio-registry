package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.storage.RegistryStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class KafkaSqlSnapshotSchedulerTest {

    private KafkaSqlSnapshotScheduler newScheduler(RegistryStorage storage) {
        KafkaSqlSnapshotScheduler scheduler = new KafkaSqlSnapshotScheduler();
        scheduler.log = LoggerFactory.getLogger(KafkaSqlSnapshotScheduler.class);
        scheduler.storage = storage;
        scheduler.registryStorageType = "kafkasql";
        scheduler.scheduledSnapshotsEnabled = () -> true;
        return scheduler;
    }

    @Test
    void testTriggersSnapshotWhenReadyAndWritable() {
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
    void testSkipsWhenGuardConditionFails(String scenario, boolean ready, boolean readOnly) {
        RegistryStorage storage = mock(RegistryStorage.class);
        when(storage.isReady()).thenReturn(ready);
        when(storage.isReadOnly()).thenReturn(readOnly);

        newScheduler(storage).run();

        verify(storage, never()).triggerSnapshotCreation();
    }

    @Test
    void testExceptionDuringSnapshotCreationIsHandled() {
        RegistryStorage storage = mock(RegistryStorage.class);
        when(storage.isReady()).thenReturn(true);
        when(storage.isReadOnly()).thenReturn(false);
        when(storage.triggerSnapshotCreation()).thenThrow(new RuntimeException("boom"));

        assertDoesNotThrow(() -> newScheduler(storage).run());
    }

    @Test
    void testDoesNotRunWhenScheduledSnapshotsDisabled() {
        RegistryStorage storage = mock(RegistryStorage.class);
        KafkaSqlSnapshotScheduler scheduler = newScheduler(storage);
        scheduler.scheduledSnapshotsEnabled = () -> false;

        scheduler.run();

        verifyNoInteractions(storage);
    }

    private static Stream<String> nonKafkaSqlStorageKinds() {
        return Stream.of("sql", "gitops", "kubernetesops");
    }

    @ParameterizedTest(name = "does not run on {0} storage")
    @MethodSource("nonKafkaSqlStorageKinds")
    void testDoesNotRunOnNonKafkaSqlStorage(String storageKind) {
        RegistryStorage storage = mock(RegistryStorage.class);
        KafkaSqlSnapshotScheduler scheduler = newScheduler(storage);
        scheduler.registryStorageType = storageKind;

        scheduler.run();

        verifyNoInteractions(storage);
    }

    @Test
    void testParseIntervalMs() {
        assertEquals(86400000L, KafkaSqlSnapshotScheduler.parseIntervalMs("86400s"));
        assertEquals(3600000L, KafkaSqlSnapshotScheduler.parseIntervalMs("3600s"));
        assertEquals(60000L, KafkaSqlSnapshotScheduler.parseIntervalMs("60s"));
        assertEquals(1000L, KafkaSqlSnapshotScheduler.parseIntervalMs("1s"));
    }

    @Test
    void testParseIntervalMsWithoutSuffix() {
        assertEquals(86400000L, KafkaSqlSnapshotScheduler.parseIntervalMs("86400"));
    }

    @Test
    void testParseIntervalMsWithWhitespace() {
        assertEquals(86400000L, KafkaSqlSnapshotScheduler.parseIntervalMs("  86400s  "));
    }
}
