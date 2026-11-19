package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.storage.impl.util.ProducerActions;
import jakarta.enterprise.inject.Instance;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaSqlSubmitterTest {

    private KafkaSqlCoordinator coordinator;

    private KafkaSqlSubmitter submitter;

    private ProducerActions<KafkaSqlMessageKey, KafkaSqlMessage> producer;

    private CompletableFuture<RecordMetadata> produceResult;

    private Instance<KafkaSqlConfiguration> configurationInstance;

    private Instance<KafkaSqlCoordinator> coordinatorInstance;

    private Instance<ProducerActions<KafkaSqlMessageKey, KafkaSqlMessage>> producerInstance;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        KafkaSqlConfiguration configuration = new KafkaSqlConfiguration();
        configuration.topic = "kafkasql-journal";
        configurationInstance = mock(Instance.class);
        when(configurationInstance.get()).thenReturn(configuration);

        // A spy so forget() can be verified directly while pendingCount() still reports real state.
        coordinator = spy(new KafkaSqlCoordinator());
        coordinator.configuration = configurationInstance;
        coordinatorInstance = mock(Instance.class);
        when(coordinatorInstance.get()).thenReturn(coordinator);

        produceResult = new CompletableFuture<>();
        producer = mock(ProducerActions.class);
        when(producer.apply(any())).thenReturn(produceResult);
        producerInstance = mock(Instance.class);
        when(producerInstance.get()).thenReturn(producer);

        submitter = new KafkaSqlSubmitter();
        submitter.configuration = configurationInstance;
        submitter.coordinator = coordinatorInstance;
        submitter.producer = producerInstance;
    }

    @Test
    void testSubmitMessageRegistersExactlyOnePendingEntry() throws Exception {
        produceResult.complete(mock(RecordMetadata.class));

        KafkaSqlMessage message = message();
        UUID requestId = submitter.submitMessage(message).get(5, TimeUnit.SECONDS);

        assertEquals(1, coordinator.pendingCount());

        ProducerRecord<KafkaSqlMessageKey, KafkaSqlMessage> record = sentRecord();
        assertEquals("kafkasql-journal", record.topic());
        assertSame(message.getKey(), record.key());
        assertSame(message, record.value());

        // The response is routed by the header, so it must match the UUID the caller waits on.
        assertEquals(requestId, sentRequestId(),
                "The registered UUID and the one on the wire must be the same");
    }

    @Test
    void testSubmitMessageWithoutResponseRegistersNoPendingEntry() throws Exception {
        produceResult.complete(mock(RecordMetadata.class));

        UUID requestId = submitter.submitMessageWithoutResponse(message()).get(5, TimeUnit.SECONDS);

        // Consumers need the type header, and a real UUID in the request ID header.
        assertEquals("RecordUsageEvent1", sentHeader(KafkaSqlSubmitter.MESSAGE_TYPE_HEADER));
        assertEquals(4, sentRequestId().version(), "The request ID must be a well-formed random UUID");
        assertEquals(requestId, sentRequestId(), "The returned UUID must be the one on the wire");
        // The submitter reaches the coordinator only through this Instance.
        verify(coordinatorInstance, never()).get();
        assertEquals(0, coordinator.pendingCount());
    }

    /**
     * A send that fails after submitMessage returns forgets the entry. The mid-flight count
     * proves the entry was registered in the first place.
     */
    @Test
    @Timeout(10)
    void testAsyncSendFailureForgetsPendingEntry() throws Exception {
        RuntimeException sendFailure = new RuntimeException("broker down");

        CompletableFuture<UUID> result = submitter.submitMessage(message());
        assertEquals(1, coordinator.pendingCount(),
                "The entry must still be pending while the send is in flight");

        produceResult.completeExceptionally(sendFailure);

        ExecutionException thrown = assertThrows(ExecutionException.class,
                () -> result.get(5, TimeUnit.SECONDS));
        assertSame(sendFailure, thrown.getCause(),
                "The caller must see the original send failure, not a substitute");

        assertEquals(0, coordinator.pendingCount(),
                "A send that failed asynchronously must not leave its entry in the pending map");
    }

    /** A synchronous throw from send() (here, the topic lookup) still forgets the entry. */
    @Test
    void testSynchronousSendFailureForgetsPendingEntry() {
        when(configurationInstance.get()).thenThrow(new IllegalStateException("configuration unavailable"));

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> submitter.submitMessage(message()));
        assertEquals("configuration unavailable", thrown.getMessage());

        assertEquals(0, coordinator.pendingCount());
        // pendingCount() alone can't tell "forgot" from "never registered".
        verify(coordinator).forget(any(UUID.class));
    }

    @Test
    void testSubmitMessageWithoutResponseSynchronousFailurePropagates() {
        when(producerInstance.get()).thenThrow(new IllegalStateException("producer unavailable"));

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> submitter.submitMessageWithoutResponse(message()));
        assertEquals("producer unavailable", thrown.getMessage());

        // This path never registers, so assert the coordinator is never consulted.
        verify(coordinatorInstance, never()).get();
    }

    /** An asynchronous failure reaches the caller through the returned future. */
    @Test
    void testSubmitMessageWithoutResponseAsyncFailureReachesTheCaller() {
        RuntimeException sendFailure = new RuntimeException("broker down");

        CompletableFuture<UUID> result = submitter.submitMessageWithoutResponse(message());
        produceResult.completeExceptionally(sendFailure);

        ExecutionException thrown = assertThrows(ExecutionException.class,
                () -> result.get(5, TimeUnit.SECONDS));
        assertSame(sendFailure, thrown.getCause(),
                "The caller must see the original send failure, not a substitute");
        verify(coordinatorInstance, never()).get();
    }

    /**
     * Bootstrap must not register a coordinator entry, since nobody waits on it. Runs in a separate
     * thread because blockOnResult swallows interrupts and would otherwise ignore the timeout.
     */
    @Test
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void testSubmitBootstrapRegistersNoPendingEntry() {
        produceResult.complete(mock(RecordMetadata.class));

        submitter.submitBootstrap("bootstrap-1");

        assertEquals(KafkaSqlSubmitter.BOOTSTRAP_MESSAGE_TYPE, sentHeader(KafkaSqlSubmitter.MESSAGE_TYPE_HEADER));
        assertEquals(4, sentRequestId().version(), "The request ID must be a well-formed random UUID");
        ProducerRecord<KafkaSqlMessageKey, KafkaSqlMessage> record = sentRecord();
        // The consumer matches a bootstrap record to its own sequence by this key UUID.
        assertEquals("bootstrap-1", record.key().getUuid());
        assertNull(record.value());

        verify(coordinatorInstance, never()).get();
    }

    /** The single record handed to the producer. */
    private ProducerRecord<KafkaSqlMessageKey, KafkaSqlMessage> sentRecord() {
        ArgumentCaptor<ProducerRecord<KafkaSqlMessageKey, KafkaSqlMessage>> record =
                ArgumentCaptor.forClass(ProducerRecord.class);
        verify(producer).apply(record.capture());
        return record.getValue();
    }

    /** Decodes a header of the single record handed to the producer. */
    private String sentHeader(String name) {
        Header header = sentRecord().headers().lastHeader(name);
        assertNotNull(header, name + " header missing");
        return new String(header.value(), StandardCharsets.UTF_8);
    }

    /** KafkaSqlSink parses this header with UUID.fromString, so anything else fails here too. */
    private UUID sentRequestId() {
        return UUID.fromString(sentHeader(KafkaSqlSubmitter.REQUEST_ID_HEADER));
    }

    private KafkaSqlMessage message() {
        KafkaSqlMessage message = mock(KafkaSqlMessage.class);
        when(message.getKey()).thenReturn(KafkaSqlMessageKey.builder()
                .messageType("RecordUsageEvent1").uuid("submitter-test").build());
        return message;
    }
}
