package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.storage.impl.util.ProducerActions;
import jakarta.enterprise.inject.Instance;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Header;
import org.jboss.logmanager.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

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

        // The returned UUID is how the caller later claims its response, and the header is how
        // KafkaSqlSink routes that response back. Nothing else pins them to the same value: a
        // send() handed a fresh UUID.randomUUID() would still register one entry and still
        // return a UUID, and every other assertion here would pass while the response was
        // delivered under an ID no one is waiting on.
        assertEquals(requestId, sentRequestId(),
                "The registered UUID and the one on the wire must be the same");
    }

    @Test
    void testSubmitFireAndForgetRegistersNoPendingEntry() {
        produceResult.complete(mock(RecordMetadata.class));

        submitter.submitFireAndForget(message());

        // KafkaSqlValueDeserializer needs the type header to decode the record. KafkaAdminUtil
        // classifies a journal topic as v3 only when the request ID header is present too, and
        // KafkaSqlSink parses that header with UUID.fromString, so it must be a real UUID.
        assertEquals("RecordUsageEvent1", sentHeader(KafkaSqlSubmitter.MESSAGE_TYPE_HEADER));
        assertEquals(4, sentRequestId().version(), "The request ID must be a well-formed random UUID");
        // The submitter reaches the coordinator only through this Instance.
        verify(coordinatorInstance, never()).get();
        assertEquals(0, coordinator.pendingCount());
    }

    /**
     * Fails the send after submitMessage has returned, which is the ordering the forget() in
     * whenComplete exists for. Asserting the entry is still pending in between is what
     * separates "forget ran" from "nothing was ever registered": without that mid-flight
     * check, a submitMessage that never called createUUID would pass just as happily.
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

    /**
     * Models a synchronous throw out of send(). AsyncProducer.apply turns an Exception from the
     * Kafka client into an exceptionally-completed future, but it is reached through a CDI client
     * proxy whose bean is created on first use, so a failure creating that bean still throws
     * from apply() itself; the Instance.get() lookups and the ProducerRecord constructor throw
     * the same way. This covers the topic lookup, which is the first of them.
     */
    @Test
    void testSynchronousSendFailureForgetsPendingEntry() {
        when(configurationInstance.get()).thenThrow(new IllegalStateException("configuration unavailable"));

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> submitter.submitMessage(message()));
        assertEquals("configuration unavailable", thrown.getMessage());

        assertEquals(0, coordinator.pendingCount());
        // A count of 0 is also the state before submitMessage runs, so on its own it cannot tell
        // "forget ran" from "createUUID was never called". Verifying forget directly states the
        // contract the catch block exists for.
        verify(coordinator).forget(any(UUID.class));
    }

    @Test
    void testFireAndForgetSynchronousFailureStillPropagates() {
        // A synchronous send failure means the record was never handed to the producer.
        when(producerInstance.get()).thenThrow(new IllegalStateException("producer unavailable"));

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> submitter.submitFireAndForget(message()));
        assertEquals("producer unavailable", thrown.getMessage());

        // Not pendingCount(): fire-and-forget never registers, so a count of 0 holds even for an
        // empty method body. Asserting the coordinator is never consulted states the contract.
        verify(coordinatorInstance, never()).get();
    }

    /**
     * The drop-and-log branch, which is the half of submitFireAndForget that the synchronous test
     * above cannot reach: that one throws from the producer lookup inside send(), before
     * whenComplete is ever attached. Here the send succeeds and fails afterwards, so the callback
     * runs.
     * <p>
     * The captured log record is the assertion rather than a nicety. Dropping the failure is the
     * only observable thing this branch does, so without it the test passes just as happily
     * against a whenComplete that was deleted outright. The logger comes from jboss-logmanager
     * directly, the same LogContext lookup the slf4j binding makes, rather than from
     * java.util.logging.Logger: that one reaches the same logger only when surefire has set
     * java.util.logging.manager, so an IDE or bare JUnit run would capture nothing.
     */
    @Test
    void testFireAndForgetAsyncFailureIsDroppedNotPropagated() {
        Logger logger = Logger.getLogger(KafkaSqlSubmitter.class.getName());
        // The logger is JVM-global, so the handler keeps only WARN records naming this test's own
        // exception; other code logging through it from another thread is ignored.
        RuntimeException sendFailure = new RuntimeException("broker down " + UUID.randomUUID());
        List<LogRecord> captured = new CopyOnWriteArrayList<>();
        Handler captor = new Handler() {
            @Override
            public void publish(LogRecord record) {
                Object[] parameters = record.getParameters();
                // equals, not ==: jboss-logmanager publishes its own WARN instance, which
                // Level.equals matches to WARNING by int value.
                if (Level.WARNING.equals(record.getLevel()) && parameters != null
                        && Arrays.asList(parameters).contains(sendFailure.toString())) {
                    captured.add(record);
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        logger.addHandler(captor);
        try {
            submitter.submitFireAndForget(message());

            produceResult.completeExceptionally(sendFailure);

            // The failure completes on this thread, so the record is published before this runs.
            assertEquals(1, captured.size(), "The dropped send must be logged exactly once");
            LogRecord record = captured.get(0);
            assertEquals("RecordUsageEvent1", record.getParameters()[0],
                    "The message type is what tells an operator which send was lost");
            assertNull(record.getThrown(),
                    "The WARN line carries no stack trace; a flush dropping thousands of messages "
                            + "during an outage would otherwise print one per message");
        } finally {
            logger.removeHandler(captor);
        }

        verify(coordinatorInstance, never()).get();
    }

    /**
     * submitBootstrap used to route through send(), which allocated a coordinator entry, so
     * every node startup registered a pending entry nobody ever waited on. It now passes a
     * random UUID instead. Nothing else pins that, so this is what stops the coordinator
     * lookup being reintroduced here. The headers are asserted too: KafkaAdminUtil classifies
     * journal topics by their presence, so dropping them would break topic detection rather
     * than anything this class can see.
     * <p>
     * The pre-completion at the start of the test is load-bearing. submitBootstrap blocks in
     * ConcurrentUtil.blockOnResult, whose retry loop swallows InterruptedException, so a
     * future that never completes would ignore a same-thread {@code @Timeout}'s interrupt and
     * hang the fork. The separate thread mode lets the timeout fail the test anyway, leaking
     * only the blocked thread.
     */
    @Test
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void testSubmitBootstrapRegistersNoPendingEntry() {
        produceResult.complete(mock(RecordMetadata.class));

        submitter.submitBootstrap("bootstrap-1");

        assertEquals(KafkaSqlSubmitter.BOOTSTRAP_MESSAGE_TYPE, sentHeader(KafkaSqlSubmitter.MESSAGE_TYPE_HEADER));
        assertEquals(4, sentRequestId().version(), "The request ID must be a well-formed random UUID");
        ProducerRecord<KafkaSqlMessageKey, KafkaSqlMessage> record = sentRecord();
        // Once the type marks a record as a bootstrap, the consumer matches it to its own
        // bootstrap sequence by this key UUID.
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
