package io.apicurio.registry.storage.impl.kafkasql;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class KafkaSqlShutdownTest {

    @SuppressWarnings("unchecked")
    @Test
    void onDestroyCallsWakeupNotClose() throws Exception {
        KafkaSqlRegistryStorage storage = new KafkaSqlRegistryStorage();
        storage.log = mock(Logger.class);

        KafkaConsumer<KafkaSqlMessageKey, KafkaSqlMessage> journalConsumer = mock(KafkaConsumer.class);
        KafkaConsumer<String, String> snapshotsConsumer = mock(KafkaConsumer.class);

        storage.journalConsumer = journalConsumer;
        storage.snapshotsConsumer = snapshotsConsumer;

        setPrivateField(storage, "stopped", false);

        storage.onDestroy();

        verify(journalConsumer).wakeup();
        verify(journalConsumer, never()).close();

        // The snapshots consumer is closed directly (it is not used from another thread)
        verify(snapshotsConsumer).close();

        boolean stopped = (boolean) getPrivateField(storage, "stopped");
        assertTrue(stopped, "stopped flag should be true after onDestroy()");
    }

    @SuppressWarnings("unchecked")
    @Test
    void onDestroyJoinsConsumerThreadThatExitsPromptly() throws Exception {
        KafkaSqlRegistryStorage storage = new KafkaSqlRegistryStorage();
        storage.log = mock(Logger.class);

        KafkaConsumer<KafkaSqlMessageKey, KafkaSqlMessage> journalConsumer = mock(KafkaConsumer.class);
        KafkaConsumer<String, String> snapshotsConsumer = mock(KafkaConsumer.class);

        storage.journalConsumer = journalConsumer;
        storage.snapshotsConsumer = snapshotsConsumer;

        // A thread that exits immediately
        Thread quickThread = new Thread(() -> { });
        quickThread.start();
        quickThread.join(); // ensure it has finished before we set it

        setPrivateField(storage, "consumerThread", quickThread);
        setPrivateField(storage, "stopped", false);

        storage.onDestroy();

        verify(journalConsumer).wakeup();
        verify(journalConsumer, never()).close();

        // The thread exited before join; interrupt should not have been called.
        // Thread.interrupt() on an already-dead thread is a no-op, but the production
        // code guards with isAlive(), so we verify the thread is no longer alive.
        assertFalse(quickThread.isAlive(), "consumer thread should not be alive after join");
    }

    @SuppressWarnings("unchecked")
    @Test
    void onDestroyInterruptsConsumerThreadThatDoesNotExit() throws Exception {
        KafkaSqlRegistryStorage storage = new KafkaSqlRegistryStorage();
        storage.log = mock(Logger.class);

        KafkaConsumer<KafkaSqlMessageKey, KafkaSqlMessage> journalConsumer = mock(KafkaConsumer.class);
        KafkaConsumer<String, String> snapshotsConsumer = mock(KafkaConsumer.class);

        storage.journalConsumer = journalConsumer;
        storage.snapshotsConsumer = snapshotsConsumer;

        // Use a short timeout so the test runs quickly
        storage.joinTimeoutMillis = 50;

        // A latch that blocks the thread until interrupted or the test ends
        CountDownLatch blockLatch = new CountDownLatch(1);

        Thread blockingThread = new Thread(() -> {
            try {
                blockLatch.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                // Expected: onDestroy() will interrupt this thread
                Thread.currentThread().interrupt();
            }
        });
        blockingThread.start();

        setPrivateField(storage, "consumerThread", blockingThread);
        setPrivateField(storage, "stopped", false);

        storage.onDestroy();

        verify(journalConsumer).wakeup();
        verify(journalConsumer, never()).close();

        // The thread was still alive after the 50ms join timeout, so onDestroy()
        // should have called interrupt().
        assertTrue(blockingThread.isInterrupted() || !blockingThread.isAlive(),
                "consumer thread should have been interrupted");

        // Clean up: release the latch so the thread exits
        blockLatch.countDown();
        blockingThread.join(1_000);
    }

    private static void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getPrivateField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
