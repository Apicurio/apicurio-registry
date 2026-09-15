package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.storage.impl.util.ProducerActions;
import jakarta.enterprise.inject.Instance;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaSqlSubmitterTest {

    private KafkaSqlCoordinator coordinator;

    private KafkaSqlSubmitter submitter;

    private ProducerActions<KafkaSqlMessageKey, KafkaSqlMessage> producer;

    private CompletableFuture<RecordMetadata> produceResult;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        KafkaSqlConfiguration configuration = new KafkaSqlConfiguration();
        configuration.topic = "kafkasql-journal";
        configuration.responseTimeout = 30000;
        Instance<KafkaSqlConfiguration> configurationInstance = mock(Instance.class);
        when(configurationInstance.get()).thenReturn(configuration);

        coordinator = new KafkaSqlCoordinator();
        coordinator.configuration = configurationInstance;
        Instance<KafkaSqlCoordinator> coordinatorInstance = mock(Instance.class);
        when(coordinatorInstance.get()).thenReturn(coordinator);

        produceResult = new CompletableFuture<>();
        producer = mock(ProducerActions.class);
        when(producer.apply(any())).thenReturn(produceResult);
        Instance<ProducerActions<KafkaSqlMessageKey, KafkaSqlMessage>> producerInstance = mock(Instance.class);
        when(producerInstance.get()).thenReturn(producer);

        submitter = new KafkaSqlSubmitter();
        submitter.storageType = "kafkasql";
        submitter.configuration = configurationInstance;
        submitter.coordinator = coordinatorInstance;
        submitter.producer = producerInstance;
    }

    @Test
    void testSubmitMessageRegistersExactlyOnePendingEntry() throws Exception {
        produceResult.complete(mock(RecordMetadata.class));

        UUID requestId = submitter.submitMessage(message()).get();

        assertEquals(1, coordinator.pendingCount());

        coordinator.notifyResponse(requestId, "done");
        coordinator.waitForResponse(requestId);
        assertEquals(0, coordinator.pendingCount());
    }

    @Test
    void testSubmitFireAndForgetRegistersNoPendingEntry() {
        produceResult.complete(mock(RecordMetadata.class));

        submitter.submitFireAndForget(message());

        verify(producer).apply(any(ProducerRecord.class));
        assertEquals(0, coordinator.pendingCount());
    }

    @Test
    void testAsyncSendFailureForgetsPendingEntry() {
        produceResult.completeExceptionally(new RuntimeException("broker down"));

        CompletableFuture<UUID> submitted = submitter.submitMessage(message());

        assertEquals(0, coordinator.pendingCount());
        assertTrue(submitted.isCompletedExceptionally(),
                "The send failure must still surface to the caller");
    }

    @Test
    void testSynchronousSendFailureForgetsPendingEntry() {
        when(producer.apply(any())).thenThrow(new RuntimeException("buffer full"));

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> submitter.submitMessage(message()));
        assertEquals("buffer full", thrown.getMessage());

        assertEquals(0, coordinator.pendingCount());
    }

    @Test
    void testFireAndForgetSynchronousFailureStillPropagates() {
        // A synchronous send failure has always propagated to the caller (the record was
        // never handed to the producer); only failures surfacing asynchronously are
        // dropped with a warning. Keeping that split preserves the pre-existing contract.
        when(producer.apply(any())).thenThrow(new RuntimeException("buffer full"));

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> submitter.submitFireAndForget(message()));
        assertEquals("buffer full", thrown.getMessage());

        assertEquals(0, coordinator.pendingCount());
    }

    private KafkaSqlMessage message() {
        KafkaSqlMessage message = mock(KafkaSqlMessage.class);
        when(message.getKey()).thenReturn(KafkaSqlMessageKey.builder()
                .messageType("RecordUsageEvent1").uuid("submitter-test").build());
        return message;
    }
}
