package io.apicurio.registry.rest;

import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.UsageTelemetryConfig;
import io.apicurio.registry.storage.dto.SchemaUsageEventDto;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
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

        assertEquals(1, buffer(telemetry).size());
    }

    @Test
    void testEventIsBufferedAgainOnceTheDedupWindowHasPassed() throws Exception {
        UsageTelemetryBuffer telemetry = new UsageTelemetryBuffer();
        telemetry.addEvent(event(1));

        dedupMap(telemetry).put("g1:" + CLIENT_ID, System.currentTimeMillis() - 120_000);
        telemetry.addEvent(event(1));

        assertEquals(2, buffer(telemetry).size());
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
            new Thread(() -> {
                try {
                    start.await();
                    for (int key = 1; key <= KEYS; key++) {
                        telemetry.addEvent(event(key));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finished.countDown();
                }
            }).start();
        }

        start.countDown();
        assertTrue(finished.await(30, TimeUnit.SECONDS), "threads did not finish in time");
        assertEquals(KEYS, buffer(telemetry).size());
    }

    /**
     * A flush stops once it has collected {@code MAX_BUFFER_SIZE * 2} events, leaving the rest for the
     * next cycle. The check that enforces that limit must happen before an event is removed from the
     * buffer, otherwise the event that trips the limit is dequeued, never added to the batch, and lost.
     *
     * <p>Filling the buffer with more events than a single flush can hold and then draining it exercises
     * that boundary: every buffered event has to reach storage exactly once.
     */
    @Test
    void testFlushDoesNotLoseEventsWhenTheBufferExceedsTheBatchLimit() throws Exception {
        UsageTelemetryBuffer telemetry = new UsageTelemetryBuffer();
        UsageTelemetryConfig config = mock(UsageTelemetryConfig.class);
        when(config.isEnabled()).thenReturn(true);
        RegistryStorage storage = mock(RegistryStorage.class);
        Set<Long> recorded = new HashSet<>();
        doAnswer(invocation -> {
            recorded.add(((SchemaUsageEventDto) invocation.getArgument(0)).getGlobalId());
            return null;
        }).when(storage).recordUsageEvent(any());

        setField(telemetry, "config", config);
        setField(telemetry, "storage", storage);
        setField(telemetry, "log", mock(Logger.class));

        // More than 2 * MAX_BUFFER_SIZE (2000), so a single flush cannot drain the buffer.
        int total = 2500;
        for (int globalId = 1; globalId <= total; globalId++) {
            telemetry.addEvent(event(globalId));
        }
        assertEquals(total, buffer(telemetry).size());

        for (int i = 0; i < 10 && !buffer(telemetry).isEmpty(); i++) {
            telemetry.flush();
        }

        assertEquals(total, recorded.size(),
                "every buffered event must reach storage exactly once across the flush cycles");
    }

    private static SchemaUsageEventDto event(long globalId) {
        return SchemaUsageEventDto.builder().globalId(globalId).clientId(CLIENT_ID)
                .operation("read").eventTimestamp(System.currentTimeMillis()).build();
    }

    @SuppressWarnings("unchecked")
    private static Collection<SchemaUsageEventDto> buffer(UsageTelemetryBuffer telemetry)
            throws Exception {
        return (Collection<SchemaUsageEventDto>) field(telemetry, "buffer");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Long> dedupMap(UsageTelemetryBuffer telemetry) throws Exception {
        return (Map<String, Long>) field(telemetry, "dedupMap");
    }

    private static Object field(UsageTelemetryBuffer telemetry, String name) throws Exception {
        Field field = UsageTelemetryBuffer.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(telemetry);
    }

    private static void setField(UsageTelemetryBuffer telemetry, String name, Object value)
            throws Exception {
        Field field = UsageTelemetryBuffer.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(telemetry, value);
    }
}
