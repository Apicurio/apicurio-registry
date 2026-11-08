package io.apicurio.registry.rest;

import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.UsageTelemetryConfig;
import io.apicurio.registry.storage.dto.SchemaUsageEventDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UsageTelemetryBufferTest {

    private static final int THREADS = 8;
    private static final int KEYS = 200;
    private static final String CLIENT_ID = "client";

    @Test
    void testDuplicateEventWithinDedupWindowIsDropped() throws Exception {
        UsageTelemetryBuffer telemetry = new UsageTelemetryBuffer();

        telemetry.addEvent(event(1));
        telemetry.addEvent(event(1));

        assertEquals(1, telemetry.buffer.size());
    }

    @Test
    void testEventIsBufferedAgainOnceTheDedupWindowHasPassed() throws Exception {
        UsageTelemetryBuffer telemetry = new UsageTelemetryBuffer();
        telemetry.addEvent(event(1));

        telemetry.dedupMap.put("g1:" + CLIENT_ID, System.currentTimeMillis() - 120_000);
        telemetry.addEvent(event(1));

        assertEquals(2, telemetry.buffer.size());
    }

    /**
     * Every key is offered by all threads at once. The dedup window is a minute, so a correct
     * implementation buffers each key exactly once no matter how the threads interleave.
     */
    @Test
    void testConcurrentAddEventBuffersEachKeyExactlyOnce() throws Exception {
        UsageTelemetryBuffer telemetry = new UsageTelemetryBuffer();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(THREADS);

        for (int i = 0; i < THREADS; i++) {
            Thread writer = new Thread(() -> {
                try {
                    if (!start.await(30, TimeUnit.SECONDS)) {
                        return;
                    }
                    for (int key = 1; key <= KEYS; key++) {
                        telemetry.addEvent(event(key));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finished.countDown();
                }
            });
            writer.setDaemon(true);
            writer.start();
        }

        start.countDown();
        assertTrue(finished.await(30, TimeUnit.SECONDS), "threads did not finish in time");
        assertEquals(KEYS, telemetry.buffer.size());
    }

    /**
     * The dedup window keeps one event per key, but the flush still hands its whole batch to the storage in
     * one call, so a storage that folds events by day can do so across the batch.
     */
    @Test
    void testFlushHandsTheWholeBatchToTheStorageInOneCall() throws Exception {
        UsageTelemetryBuffer telemetry = new UsageTelemetryBuffer();
        telemetry.storage = mock(RegistryStorage.class);
        telemetry.config = mock(UsageTelemetryConfig.class);
        when(telemetry.config.isEnabled()).thenReturn(true);
        SchemaUsageEventDto first = event(1);
        SchemaUsageEventDto second = event(2);
        telemetry.addEvent(first);
        telemetry.addEvent(second);

        telemetry.flush();

        verify(telemetry.storage).recordUsageEvents(List.of(first, second));
        verify(telemetry.storage, never()).recordUsageEvent(any());
        assertEquals(0, telemetry.buffer.size());
    }

    private static SchemaUsageEventDto event(long globalId) {
        return SchemaUsageEventDto.builder().globalId(globalId).clientId(CLIENT_ID)
                .operation("read").eventTimestamp(System.currentTimeMillis()).build();
    }
}
