package io.apicurio.registry.storage.impl.kafkasql.sql;

import io.apicurio.registry.storage.error.VersionAlreadyExistsException;
import io.apicurio.registry.storage.impl.kafkasql.KafkaSqlCoordinator;
import io.apicurio.registry.storage.impl.kafkasql.KafkaSqlMessage;
import io.apicurio.registry.storage.impl.kafkasql.KafkaSqlMessageKey;
import io.apicurio.registry.storage.impl.sql.SqlRegistryStorage;
import io.apicurio.registry.storage.impl.sql.jdb.RuntimeSqlException;
import jakarta.enterprise.inject.Instance;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.sql.SQLException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * During journal replay no thread waits on the coordinator, so the sink's own log is the only
 * record of a message the database refused.
 */
class KafkaSqlSinkTest {

    private final Logger log = mock(Logger.class);
    private final KafkaSqlCoordinator coordinator = mock(KafkaSqlCoordinator.class);
    private final KafkaSqlMessage message = mock(KafkaSqlMessage.class);
    private KafkaSqlSink sink;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        sink = new KafkaSqlSink();
        sink.log = log;
        sink.sqlStore = mock(SqlRegistryStorage.class);
        sink.coordinator = mock(Instance.class);
        when(sink.coordinator.get()).thenReturn(coordinator);
    }

    @Test
    void sqlRejectionIsLoggedAtWarnAndStillReported() {
        RuntimeSqlException rejected = new RuntimeSqlException(
                new SQLException("Unique index or primary key violation: UQ_versions_3"));
        when(message.dispatchTo(any())).thenThrow(rejected);

        sink.processMessage(consumerRecord("ImportArtifactVersion1Message"));

        verify(log).warn("Kafka message {} at partition {} offset {} was not applied: {}",
                "ImportArtifactVersion1Message", 0, 42L, rejected.getMessage());
        verify(log, never()).debug(eq("Runtime exception detected: {}"), any(Object.class));
        verify(coordinator).notifyResponse(any(), same(rejected));
    }

    @Test
    void domainExceptionStaysAtDebug() {
        VersionAlreadyExistsException conflict = new VersionAlreadyExistsException("g", "a", "1");
        when(message.dispatchTo(any())).thenThrow(conflict);

        sink.processMessage(consumerRecord("CreateArtifactVersion8Message"));

        verify(log, never()).warn(anyString(), any(Object[].class));
        verify(log).debug("Runtime exception detected: {}", conflict.getMessage());
        verify(coordinator).notifyResponse(any(), same(conflict));
    }

    private ConsumerRecord<KafkaSqlMessageKey, KafkaSqlMessage> consumerRecord(String messageType) {
        return new ConsumerRecord<>("journal", 0, 42L,
                KafkaSqlMessageKey.builder().messageType(messageType).build(), message);
    }
}
