package io.apicurio.registry.rest;

import io.apicurio.registry.storage.dto.SchemaUsageEventDto;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
