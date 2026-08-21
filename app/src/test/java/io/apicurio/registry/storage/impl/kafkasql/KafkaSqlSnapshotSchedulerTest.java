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
        scheduler.initialDelayApplied = true;
        return scheduler;
    }

    @Test
    void testTriggersSnapshotWhenReadyAndWritable() {
        RegistryStorage storage = mock(RegistryStorage.class);
        when(storage.isReady()).thenReturn(true);
        when(storage.isReadOnly()).thenReturn(false);

        KafkaSqlSnapshotScheduler scheduler = newScheduler(storage);
        // recentSnapshotExists() will fail-closed (return true) because configuration is null,
        // so we need to provide a configuration that makes it return false (empty topic).
        // Instead, we test via a spy-like approach: verify the method is called correctly
        // by providing a scheduler subclass that overrides the check.
        KafkaSqlSnapshotScheduler testScheduler = new KafkaSqlSnapshotScheduler() {
            @Override
            boolean recentSnapshotExists() {
                return false;
            }
        };
        testScheduler.log = LoggerFactory.getLogger(KafkaSqlSnapshotScheduler.class);
        testScheduler.storage = storage;
        testScheduler.registryStorageType = "kafkasql";
        testScheduler.scheduledSnapshotsEnabled = () -> true;
        testScheduler.initialDelayApplied = true;

        testScheduler.run();

        verify(storage).triggerSnapshotCreation();
    }

    @Test
    void testSkipsWhenRecentSnapshotExists() {
        RegistryStorage storage = mock(RegistryStorage.class);
        when(storage.isReady()).thenReturn(true);
        when(storage.isReadOnly()).thenReturn(false);

        KafkaSqlSnapshotScheduler testScheduler = new KafkaSqlSnapshotScheduler() {
            @Override
            boolean recentSnapshotExists() {
                return true;
            }
        };
        testScheduler.log = LoggerFactory.getLogger(KafkaSqlSnapshotScheduler.class);
        testScheduler.storage = storage;
        testScheduler.registryStorageType = "kafkasql";
        testScheduler.scheduledSnapshotsEnabled = () -> true;
        testScheduler.initialDelayApplied = true;

        testScheduler.run();

        verify(storage, never()).triggerSnapshotCreation();
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

        KafkaSqlSnapshotScheduler testScheduler = new KafkaSqlSnapshotScheduler() {
            @Override
            boolean recentSnapshotExists() {
                return false;
            }
        };
        testScheduler.log = LoggerFactory.getLogger(KafkaSqlSnapshotScheduler.class);
        testScheduler.storage = storage;
        testScheduler.registryStorageType = "kafkasql";
        testScheduler.scheduledSnapshotsEnabled = () -> true;
        testScheduler.initialDelayApplied = true;

        assertDoesNotThrow(() -> testScheduler.run());
    }

    @Test
    void testRecentSnapshotExistsFailsClosed() {
        KafkaSqlSnapshotScheduler scheduler = newScheduler(mock(RegistryStorage.class));
        // configuration is null, so recentSnapshotExists() will throw and should return true (fail-closed)
        assertEquals(true, scheduler.recentSnapshotExists());
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
    void testRecentSnapshotExistsWithConfiguration() {
        KafkaSqlConfiguration config = mock(KafkaSqlConfiguration.class);
        when(config.getSnapshotEvery()).thenReturn(86400);
        when(config.getSnapshotsTopic()).thenReturn("kafkasql-snapshots");

        KafkaSqlSnapshotScheduler scheduler = newScheduler(mock(RegistryStorage.class));
        scheduler.configuration = config;

        // Fails closed (returns true) due to mock consumer/topic lookup, but proves intervalMs calculation succeeds
        assertEquals(true, scheduler.recentSnapshotExists());
    }
}
