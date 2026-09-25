package io.apicurio.registry.metrics.health;

import io.apicurio.registry.metrics.health.liveness.PersistenceExceptionLivenessCheck;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.PersistenceTimeoutReadinessCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ErrorCounterHealthCheckTest {

    static class TestHealthCheck extends AbstractErrorCounterHealthCheck {
        void setup(int threshold, int resetWindowSec, int statusResetWindowSec) {
            super.init(threshold, resetWindowSec, statusResetWindowSec);
        }

        long suspect() {
            return super.suspectSuper();
        }

        void call() {
            super.callSuper();
        }
    }

    @Test
    void testBasicErrorCountingAndThreshold() {
        TestHealthCheck check = new TestHealthCheck();
        check.setup(2, 60, 300);

        Assertions.assertTrue(check.isUp());
        Assertions.assertEquals(0, check.getErrorCounter());

        long c1 = check.suspect();
        Assertions.assertEquals(1, c1);
        Assertions.assertTrue(check.isUp());
        Assertions.assertEquals(1, check.getErrorCounter());

        long c2 = check.suspect();
        Assertions.assertEquals(2, c2);
        Assertions.assertTrue(check.isUp());
        Assertions.assertEquals(2, check.getErrorCounter());

        // Exceeds threshold (threshold is 2, 3 > 2)
        long c3 = check.suspect();
        Assertions.assertEquals(3, c3);
        Assertions.assertFalse(check.isUp());
        Assertions.assertEquals(3, check.getErrorCounter());
    }

    @Test
    void testConcurrentSuspectSuperNoLostUpdates() throws InterruptedException {
        TestHealthCheck check = new TestHealthCheck();
        int threshold = 10000;
        check.setup(threshold, 60, 300);

        int threadCount = 10;
        int incrementsPerThread = 500;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        Set<Long> recordedCounts = Collections.newSetFromMap(new ConcurrentHashMap<>());

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < incrementsPerThread; j++) {
                        long count = check.suspect();
                        recordedCounts.add(count);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        Assertions.assertTrue(completed, "All threads should complete within timeout");
        Assertions.assertEquals(threadCount * incrementsPerThread, check.getErrorCounter());
        // Every single increment returned a unique counter value
        Assertions.assertEquals(threadCount * incrementsPerThread, recordedCounts.size());
    }

    @Test
    void testPersistenceExceptionLivenessCheckConcurrency() throws Exception {
        PersistenceExceptionLivenessCheck check = new PersistenceExceptionLivenessCheck();
        injectField(check, "log", LoggerFactory.getLogger(PersistenceExceptionLivenessCheck.class));
        injectField(check, "configErrorThreshold", 10000);
        injectField(check, "configCounterResetWindowDurationSec", 60);
        injectField(check, "configStatusResetWindowDurationSec", 300);
        injectField(check, "disableLogging", true);
        invokeInit(check);

        int threadCount = 8;
        int callsPerThread = 200;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int threadIdx = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < callsPerThread; j++) {
                        if (threadIdx % 2 == 0) {
                            check.suspect("simulated error " + j);
                        } else {
                            check.suspectWithException(new RuntimeException("simulated exception " + j));
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        Assertions.assertTrue(completed, "All threads should finish");
        Assertions.assertEquals(threadCount * callsPerThread, check.getErrorCounter());

        HealthCheckResponse response = check.call();
        Assertions.assertEquals("PersistenceExceptionLivenessCheck", response.getName());
        Assertions.assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
        Assertions.assertEquals((long) (threadCount * callsPerThread), response.getData().get().get("errorCount"));
    }

    @Test
    void testResponseErrorLivenessCheckConcurrency() throws Exception {
        ResponseErrorLivenessCheck check = new ResponseErrorLivenessCheck();
        injectField(check, "log", LoggerFactory.getLogger(ResponseErrorLivenessCheck.class));
        injectField(check, "configErrorThreshold", 10);
        injectField(check, "configCounterResetWindowDurationSec", 60);
        injectField(check, "configStatusResetWindowDurationSec", 300);
        injectField(check, "disableLogging", true);
        invokeInit(check);

        for (int i = 0; i < 11; i++) {
            check.suspect("error " + i);
        }

        HealthCheckResponse response = check.call();
        Assertions.assertEquals(HealthCheckResponse.Status.DOWN, response.getStatus());
        Assertions.assertEquals(11L, response.getData().get().get("errorCount"));
    }

    @Test
    void testPersistenceTimeoutReadinessCheckConcurrency() throws Exception {
        PersistenceTimeoutReadinessCheck check = new PersistenceTimeoutReadinessCheck();
        injectField(check, "log", LoggerFactory.getLogger(PersistenceTimeoutReadinessCheck.class));
        injectField(check, "configErrorThreshold", 5);
        injectField(check, "configCounterResetWindowDurationSec", 60);
        injectField(check, "configStatusResetWindowDurationSec", 300);
        injectField(check, "configTimeoutSec", 15);
        invokeInit(check);

        for (int i = 0; i < 6; i++) {
            check.suspect();
        }

        HealthCheckResponse response = check.call();
        Assertions.assertEquals(HealthCheckResponse.Status.DOWN, response.getStatus());
        Assertions.assertEquals(6L, response.getData().get().get("errorCount"));
    }

    private static void invokeInit(Object target) throws Exception {
        java.lang.reflect.Method method = target.getClass().getDeclaredMethod("init");
        method.setAccessible(true);
        method.invoke(target);
    }

    private static void injectField(Object target, String fieldName, Object value) throws Exception {
        Class<?> clazz = target.getClass();
        Field field = null;
        while (clazz != null && field == null) {
            try {
                field = clazz.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        if (field == null) {
            throw new NoSuchFieldException(fieldName);
        }
        field.setAccessible(true);
        field.set(target, value);
    }
}
