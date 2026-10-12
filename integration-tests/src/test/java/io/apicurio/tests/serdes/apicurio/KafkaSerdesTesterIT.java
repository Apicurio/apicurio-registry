package io.apicurio.tests.serdes.apicurio;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static io.apicurio.deployment.Constants.SMOKE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the consume helper itself, with no broker and no registry. A consume that times out must not
 * leave its consumer running: issue 10520 showed a consumer that stayed open for five minutes after its
 * test failed and then logged connection warnings once the broker was stopped.
 */
@Tag(SMOKE)
class KafkaSerdesTesterIT {

    private static final String TOPIC = "topic";

    private final KafkaSerdesTester<String, String, String> tester = new KafkaSerdesTester<>();

    @Test
    void consumeReturnsOnceTheExpectedMessagesArrive() throws Exception {
        Consumer<String, String> consumer = consumerReturning(
                new ConsumerRecord<>(TOPIC, 0, 0L, "key", "value"));

        tester.consumeMessages(consumer, TOPIC, 1, "value"::equals);

        verify(consumer).subscribe(Collections.singletonList(TOPIC));
        verify(consumer).close();
        verify(consumer, never()).wakeup();
    }

    @Test
    void consumeTimeoutWakesUpAndClosesTheBlockedConsumer() {
        CountDownLatch woken = new CountDownLatch(1);
        Consumer<String, String> consumer = mockConsumer();
        // Models KafkaConsumer: a blocked poll() ends with a WakeupException once wakeup() is called.
        // The bound keeps the consumer thread from parking for the rest of the JVM if wakeup() is lost.
        when(consumer.poll(any(Duration.class))).thenAnswer(invocation -> {
            woken.await(30, TimeUnit.SECONDS);
            throw new WakeupException();
        });
        doAnswer(invocation -> {
            woken.countDown();
            return null;
        }).when(consumer).wakeup();

        // One expected message gives a 2.7 s wait.
        assertThrows(TimeoutException.class, () -> tester.consumeMessages(consumer, TOPIC, 1, null));

        verify(consumer).wakeup();
        // The consumer thread has exited by now, so close() has already run.
        verify(consumer).close();
    }

    @Test
    void consumeTimeoutWaitsForTheConsumerThreadBeforeReturningControl() {
        CountDownLatch woken = new CountDownLatch(1);
        AtomicBoolean pollExited = new AtomicBoolean();
        Consumer<String, String> consumer = mockConsumer();
        // poll() needs a moment to unwind after wakeup(), as a real consumer inside a network wait does.
        when(consumer.poll(any(Duration.class))).thenAnswer(invocation -> {
            woken.await(30, TimeUnit.SECONDS);
            Thread.sleep(300);
            pollExited.set(true);
            throw new WakeupException();
        });
        doAnswer(invocation -> {
            woken.countDown();
            return null;
        }).when(consumer).wakeup();

        assertThrows(TimeoutException.class, () -> tester.consumeMessages(consumer, TOPIC, 1, null));

        // A caller that reuses the consumer closes it right after this exception; poll() must be over.
        assertTrue(pollExited.get(), "consumeMessages returned while poll() was still running");
    }

    @Test
    void consumeTimeoutStillReportsTheTimeoutWhenTheCallerIsInterruptedWhileWaiting() throws Exception {
        CountDownLatch wakeupCalled = new CountDownLatch(1);
        CountDownLatch releasePoll = new CountDownLatch(1);
        Consumer<String, String> consumer = mockConsumer();
        // A poll() that ignores wakeup(), so the helper stays in its wait for the thread to end. The test
        // releases it at the end instead of leaving it parked.
        when(consumer.poll(any(Duration.class))).thenAnswer(invocation -> {
            releasePoll.await(30, TimeUnit.SECONDS);
            throw new WakeupException();
        });
        doAnswer(invocation -> {
            wakeupCalled.countDown();
            return null;
        }).when(consumer).wakeup();
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        AtomicBoolean interruptKept = new AtomicBoolean();
        Thread caller = new Thread(() -> {
            try {
                tester.consumeMessages(consumer, TOPIC, 1, null);
            } catch (Throwable t) {
                thrown.set(t);
            }
            interruptKept.set(Thread.currentThread().isInterrupted());
        });
        caller.start();
        try {
            // wakeup() is the last call before the helper waits for the thread, so its timeout has expired.
            assertTrue(wakeupCalled.await(10, TimeUnit.SECONDS));
            caller.interrupt();
            caller.join(10_000);
        } finally {
            releasePoll.countDown();
        }

        assertInstanceOf(TimeoutException.class, thrown.get());
        assertEquals(1, thrown.get().getSuppressed().length);
        assertInstanceOf(InterruptedException.class, thrown.get().getSuppressed()[0]);
        assertTrue(interruptKept.get(), "the caller's interrupt flag was cleared");
    }

    @Test
    void consumeStopsTheConsumerWhenTheCallerIsInterruptedWhileWaitingForMessages() throws Exception {
        CountDownLatch pollEntered = new CountDownLatch(1);
        CountDownLatch woken = new CountDownLatch(1);
        Consumer<String, String> consumer = mockConsumer();
        when(consumer.poll(any(Duration.class))).thenAnswer(invocation -> {
            pollEntered.countDown();
            woken.await(30, TimeUnit.SECONDS);
            throw new WakeupException();
        });
        doAnswer(invocation -> {
            woken.countDown();
            return null;
        }).when(consumer).wakeup();
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread caller = new Thread(() -> {
            try {
                tester.consumeMessages(consumer, TOPIC, 1, null);
            } catch (Throwable t) {
                thrown.set(t);
            }
        });
        caller.start();
        assertTrue(pollEntered.await(10, TimeUnit.SECONDS));
        // The caller is now inside the helper's timed wait for messages, long before it expires.
        caller.interrupt();
        caller.join(10_000);

        assertInstanceOf(InterruptedException.class, thrown.get());
        verify(consumer).wakeup();
        verify(consumer).close();
    }

    @Test
    void consumeTimeoutIsStillReportedWhenWakeupFails() {
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean pollExited = new AtomicBoolean();
        Consumer<String, String> consumer = mockConsumer();
        when(consumer.poll(any(Duration.class))).thenAnswer(invocation -> {
            release.await(30, TimeUnit.SECONDS);
            Thread.sleep(300);
            pollExited.set(true);
            throw new WakeupException();
        });
        IllegalStateException wakeupFailure = new IllegalStateException("consumer already closed");
        doAnswer(invocation -> {
            release.countDown();
            throw wakeupFailure;
        }).when(consumer).wakeup();

        try {
            TimeoutException e = assertThrows(TimeoutException.class,
                    () -> tester.consumeMessages(consumer, TOPIC, 1, null));

            assertEquals(1, e.getSuppressed().length);
            assertSame(wakeupFailure, e.getSuppressed()[0]);
            // The helper still waited for the consumer thread after the failed wakeup().
            assertTrue(pollExited.get(), "consumeMessages returned while poll() was still running");
        } finally {
            release.countDown();
        }
    }

    @Test
    void consumeReportsAValidatorFailureAndStillClosesTheConsumer() {
        Consumer<String, String> consumer = consumerReturning(
                new ConsumerRecord<>(TOPIC, 0, 0L, "key", "unexpected"));

        ExecutionException e = assertThrows(ExecutionException.class,
                () -> tester.consumeMessages(consumer, TOPIC, 1, "value"::equals));

        assertInstanceOf(AssertionError.class, e.getCause());
        String message = e.getCause().getMessage();
        assertTrue(message.startsWith("Consumed record validation failed"), () -> "actual: " + message);
        // close() runs in the helper's finally block before the failure reaches the caller.
        verify(consumer).close();
    }

    @SuppressWarnings("unchecked")
    private static Consumer<String, String> mockConsumer() {
        return mock(Consumer.class);
    }

    private static Consumer<String, String> consumerReturning(ConsumerRecord<String, String> record) {
        Consumer<String, String> consumer = mockConsumer();
        TopicPartition partition = new TopicPartition(record.topic(), record.partition());
        when(consumer.poll(any(Duration.class)))
                .thenReturn(new ConsumerRecords<>(Map.of(partition, List.of(record))));
        return consumer;
    }
}
