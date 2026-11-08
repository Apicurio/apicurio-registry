package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.storage.dto.SchemaUsageEventDto;
import io.apicurio.registry.storage.impl.kafkasql.messages.DeleteOldUsageEvents1Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.RecordUsageEvent1Message;
import io.apicurio.registry.storage.impl.util.ProducerActions;
import jakarta.enterprise.inject.Instance;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Usage writes are fire and forget. Every message registered with the coordinator keeps a latch for the life
 * of the process unless somebody waits for it, so these writes must not register one.
 */
class KafkaSqlUsageSubmissionTest {

    private KafkaSqlCoordinator coordinator;
    private ProducerActions<KafkaSqlMessageKey, KafkaSqlMessage> producer;
    private KafkaSqlSubmitter submitter;
    private KafkaSqlRegistryStorage storage;
    private Logger log;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        coordinator = new KafkaSqlCoordinator();
        producer = mock(ProducerActions.class);
        when(producer.apply(any())).thenReturn(CompletableFuture.completedFuture(mock(RecordMetadata.class)));

        KafkaSqlConfiguration configuration = mock(KafkaSqlConfiguration.class);
        when(configuration.getTopic()).thenReturn("kafkasql-journal");

        Instance<KafkaSqlConfiguration> configurationInstance = mock(Instance.class);
        when(configurationInstance.get()).thenReturn(configuration);
        Instance<KafkaSqlCoordinator> coordinatorInstance = mock(Instance.class);
        when(coordinatorInstance.get()).thenReturn(coordinator);
        Instance<ProducerActions<KafkaSqlMessageKey, KafkaSqlMessage>> producerInstance = mock(Instance.class);
        when(producerInstance.get()).thenReturn(producer);

        submitter = new KafkaSqlSubmitter();
        submitter.configuration = configurationInstance;
        submitter.coordinator = coordinatorInstance;
        submitter.producer = producerInstance;

        log = mock(Logger.class);
        storage = new KafkaSqlRegistryStorage();
        storage.log = log;
        storage.submitter = submitter;
    }

    @Test
    void usageWritesDoNotGrowTheCoordinator() {
        assertEquals(0, coordinator.pendingCount());

        for (int i = 0; i < 100; i++) {
            storage.recordUsageEvent(SchemaUsageEventDto.builder().globalId(i).contentId(0)
                    .clientId("client").operation("SERIALIZE").eventTimestamp(1000L + i).build());
        }
        storage.deleteOldUsageEvents(5000L);

        assertEquals(0, coordinator.pendingCount());
    }

    /**
     * KafkaSqlRegistryStorage inherits delegation to the local SQL store, so a batch must still go through
     * the journal, one record per event, for every replica to apply it.
     */
    @Test
    void aBatchOfUsageEventsIsJournaledOneRecordPerEvent() {
        storage.recordUsageEvents(List.of(
                SchemaUsageEventDto.builder().globalId(1).contentId(0).clientId("client").operation("SERIALIZE")
                        .eventTimestamp(1000L).build(),
                SchemaUsageEventDto.builder().globalId(1).contentId(0).clientId("client").operation("SERIALIZE")
                        .eventTimestamp(2000L).build()));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ProducerRecord<KafkaSqlMessageKey, KafkaSqlMessage>> captor = ArgumentCaptor
                .forClass(ProducerRecord.class);
        verify(producer, times(2)).apply(captor.capture());
        assertEquals(List.of(1000L, 2000L), captor.getAllValues().stream()
                .map(sent -> ((RecordUsageEvent1Message) sent.value()).getEventTimestamp()).toList());
        assertEquals(List.of("RecordUsageEvent1Message", "RecordUsageEvent1Message"), captor.getAllValues()
                .stream().map(sent -> sent.key().getMessageType()).toList());
    }

    @Test
    void awaitedMessagesStillRegisterWithTheCoordinator() {
        submitter.submitMessage(new DeleteOldUsageEvents1Message(5000L));

        assertEquals(1, coordinator.pendingCount(),
                "the control: a message somebody may wait for keeps its latch until waitForResponse");
    }

    @Test
    void usageWritesStillCarryARequestIdAndTheMessageType() {
        storage.deleteOldUsageEvents(5000L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ProducerRecord<KafkaSqlMessageKey, KafkaSqlMessage>> captor = ArgumentCaptor
                .forClass(ProducerRecord.class);
        verify(producer).apply(captor.capture());
        ProducerRecord<KafkaSqlMessageKey, KafkaSqlMessage> sent = captor.getValue();
        assertEquals("DeleteOldUsageEvents1Message", sent.key().getMessageType());
        String requestId = new String(sent.headers().lastHeader(KafkaSqlSubmitter.REQUEST_ID_HEADER).value(),
                StandardCharsets.UTF_8);
        assertNotNull(requestId, "the request id header must be present");
        assertDoesNotThrow(() -> UUID.fromString(requestId), "the request id must be a UUID");
        assertEquals("DeleteOldUsageEvents1Message", new String(
                sent.headers().lastHeader(KafkaSqlSubmitter.MESSAGE_TYPE_HEADER).value(), StandardCharsets.UTF_8));
    }

    @Test
    void aFailedSendIsLoggedAtWarnWithItsCause() {
        RuntimeException cause = new RuntimeException("broker unavailable");
        when(producer.apply(any())).thenReturn(CompletableFuture.failedFuture(cause));

        storage.deleteOldUsageEvents(5000L);

        verify(log).warn("Failed to send {} to the KafkaSQL journal.", "DeleteOldUsageEvents1Message", cause);
        assertEquals(0, coordinator.pendingCount());
    }

    @Test
    void repeatedFailuresAreLoggedAtWarnOnceAndAtDebugAfterwards() {
        RuntimeException cause = new RuntimeException("broker unavailable");
        when(producer.apply(any())).thenReturn(CompletableFuture.failedFuture(cause));

        storage.deleteOldUsageEvents(5000L);
        storage.deleteOldUsageEvents(6000L);
        storage.deleteOldUsageEvents(7000L);

        verify(log, times(1)).warn("Failed to send {} to the KafkaSQL journal.", "DeleteOldUsageEvents1Message",
                cause);
        verify(log, times(2)).debug("Failed to send {} to the KafkaSQL journal.", "DeleteOldUsageEvents1Message",
                cause);
    }
}
