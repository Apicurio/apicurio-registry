package io.apicurio.registry.storage.impl.polling;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

import io.apicurio.registry.storage.impl.polling.sql.BlueSqlStorage;
import io.apicurio.registry.storage.impl.polling.sql.GreenSqlStorage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AbstractPollingRegistryStorageTest {

    @Mock
    private PollingStorageConfig pollingConfig;

    @Mock
    private PollingDataSourceManager<TestMarker> pollingDataSourceManager;

    @Mock
    private BlueSqlStorage blue;

    @Mock
    private GreenSqlStorage green;

    @Mock
    private Logger log;

    private TestStorage storage;

    @BeforeEach
    void setUp() throws Exception {
        when(pollingConfig.getStorageName()).thenReturn("test");
        when(pollingConfig.getPollPeriod()).thenReturn(Duration.ZERO);

        storage = new TestStorage();

        setField("pollingConfig", pollingConfig);
        setField("pollingDataSourceManager", pollingDataSourceManager);
        setField("blue", blue);
        setField("green", green);
        setField("log", log);
        setField("active", green);
        setField("inactive", blue);
        setField("initialized", true);
        setField(
                "debouncer",
                new Debouncer<PollingResult<TestMarker>>(
                        Duration.ZERO,
                        Duration.ZERO
                )
        );
    }

    /*
     * This test intentionally uses reflection to inject the private dependencies of
     * AbstractPollingRegistryStorage. The test targets the abstract polling failure
     * and recovery path directly and needs deterministic control over poll() failures.
     * The existing GitOpsStatusTest covers the same behavior through the full Quarkus
     * GitOps integration setup.
     */
    private void setField(String name, Object value) throws Exception {
        Field field = AbstractPollingRegistryStorage.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(storage, value);
    }

    @Test
    void pollFailureIsReported() throws Exception {
        when(pollingDataSourceManager.poll())
                .thenThrow(new RuntimeException());

        storage.refresh();

        assertEquals(
                PollingStorageStatus.SyncState.ERROR,
                storage.status().getSyncState()
        );
        assertTrue(
                storage.status().getErrors().get(0).detail()
                        .contains("RuntimeException")
        );
        assertTrue(storage.status().getLastSyncAttempt() != null);
    }

    @Test
    void successfulPollClearsError() throws Exception {
        when(pollingDataSourceManager.poll())
                .thenThrow(new RuntimeException())
                .thenReturn(PollingResult.noChanges(new TestMarker()));

        storage.refresh();

        assertEquals(
                PollingStorageStatus.SyncState.ERROR,
                storage.status().getSyncState()
        );

        storage.refresh();

        assertEquals(
                PollingStorageStatus.SyncState.IDLE,
                storage.status().getSyncState()
        );
        assertEquals(
                Collections.emptyList(),
                storage.status().getErrors()
        );
    }

    private static class TestStorage extends AbstractPollingRegistryStorage<TestMarker> {

        void refresh() {
            tryRefresh();
        }

        PollingStorageStatus status() {
            return getStatus();
        }

        @Override
        public void initialize() {
            // Test initialization is performed through reflection in setUp().
        }
    }

    private record TestMarker() implements SourceMarker {

        @Override
        public Map<String, String> toSources() {
            return Collections.emptyMap();
        }

        @Override
        public Instant getCommitTime() {
            return Instant.EPOCH;
        }
    }
}
