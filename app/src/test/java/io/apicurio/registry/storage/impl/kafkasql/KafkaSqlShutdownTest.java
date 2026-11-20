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
    void onDestroyClosesJournalConsumerWhenConsumerThreadNeverStarted() throws Exception {
        KafkaSqlRegistryStorage storage = new KafkaSqlRegistryStorage();
        storage.log = mock(Logger.class);

        KafkaConsumer<KafkaSqlMessageKey, KafkaSqlMessage> journalConsumer = mock(KafkaConsumer.class);
        KafkaConsumer<String, String> snapshotsConsumer = mock(KafkaConsumer.class);

        storage.journalConsumer = journalConsumer;
        storage.snapshotsConsumer = snapshotsConsumer;

        setPrivateField(storage, "stopped", false);

        // consumerThread stays null: the state after initialize() fails before it reaches
        // startConsumerThread().
        storage.onDestroy();

        verify(journalConsumer).close();
        // Nothing is polling, so there is nothing to wake up.
        verify(journalConsumer, never()).wakeup();

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
        RecordingThread quickThread = new RecordingThread(() -> { });
        quickThread.start();
        quickThread.join(1_000);
        assertFalse(quickThread.isAlive(), "test setup: the quick thread should have exited");

        setPrivateField(storage, "consumerThread", quickThread);
        setPrivateField(storage, "stopped", false);

        storage.onDestroy();

        verify(journalConsumer).wakeup();
        verify(journalConsumer, never()).close();

        // The thread had already exited, so the isAlive() guard in onDestroy() must have
        // skipped interrupt() entirely.
        assertFalse(quickThread.wasInterrupted(),
                "onDestroy() should not interrupt a thread that already exited");
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

        RecordingThread blockingThread = new RecordingThread(() -> {
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

        // The thread was still alive when the 50ms join expired, so onDestroy() must have
        // called interrupt(). Assert on the recorded call rather than on isInterrupted():
        // if the join stopped honouring joinTimeoutMillis, the thread would sit on the
        // latch for its full 30s, exit on its own, and leave isInterrupted() false with
        // isAlive() false too, which an "interrupted or dead" disjunction would accept.
        assertTrue(blockingThread.wasInterrupted(),
                "onDestroy() should interrupt a consumer thread that does not exit");

        // Clean up: release the latch so the thread exits
        blockLatch.countDown();
        blockingThread.join(1_000);
    }

    /**
     * A Thread that records whether interrupt() was called on it.
     *
     * The recorded call is the assertable signal, not Thread.isInterrupted(). The JVM
     * clears the interrupt flag when it delivers InterruptedException to a blocked call,
     * so the flag is only set again if the thread's own catch block re-interrupts, and it
     * is not reliably readable once the thread has terminated.
     */
    private static final class RecordingThread extends Thread {

        private volatile boolean interruptCalled = false;

        RecordingThread(Runnable target) {
            super(target);
        }

        @Override
        public void interrupt() {
            interruptCalled = true;
            super.interrupt();
        }

        boolean wasInterrupted() {
            return interruptCalled;
        }
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
