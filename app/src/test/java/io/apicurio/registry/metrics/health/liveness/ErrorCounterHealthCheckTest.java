package io.apicurio.registry.metrics.health.liveness;

import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ErrorCounterHealthCheckTest {

    @Test
    void testBasicErrorCountingAndThreshold() {
        PersistenceExceptionLivenessCheck check = new PersistenceExceptionLivenessCheck();
        check.log = Mockito.mock(Logger.class);
        check.configErrorThreshold = 2;
        check.configCounterResetWindowDurationSec = 60;
        check.configStatusResetWindowDurationSec = 300;
        check.disableLogging = true;
        check.init();

        HealthCheckResponse initialResponse = check.call();
        Assertions.assertEquals(HealthCheckResponse.Status.UP, initialResponse.getStatus());
        Assertions.assertEquals(0L, initialResponse.getData().get().get("errorCount"));

        check.suspect("error 1");
        HealthCheckResponse r1 = check.call();
        Assertions.assertEquals(HealthCheckResponse.Status.UP, r1.getStatus());
        Assertions.assertEquals(1L, r1.getData().get().get("errorCount"));

        check.suspect("error 2");
        HealthCheckResponse r2 = check.call();
        Assertions.assertEquals(HealthCheckResponse.Status.UP, r2.getStatus());
        Assertions.assertEquals(2L, r2.getData().get().get("errorCount"));

        // Exceeds threshold (threshold is 2, 3 > 2)
        check.suspect("error 3");
        HealthCheckResponse r3 = check.call();
        Assertions.assertEquals(HealthCheckResponse.Status.DOWN, r3.getStatus());
        Assertions.assertEquals(3L, r3.getData().get().get("errorCount"));
    }

    @Test
    void testPersistenceExceptionLivenessCheckDistinctLoggedCountsUnderConcurrency() throws Exception {
        PersistenceExceptionLivenessCheck check = new PersistenceExceptionLivenessCheck();
        check.configErrorThreshold = 10000;
        check.configCounterResetWindowDurationSec = 60;
        check.configStatusResetWindowDurationSec = 300;
        check.disableLogging = false;

        Set<Long> loggedCounts = Collections.newSetFromMap(new ConcurrentHashMap<>());
        Logger mockLogger = (Logger) java.lang.reflect.Proxy.newProxyInstance(
                Logger.class.getClassLoader(),
                new Class<?>[]{Logger.class},
                (proxy, method, args) -> {
                    if ("info".equals(method.getName()) && args != null && args.length >= 2) {
                        String format = String.valueOf(args[0]);
                        if (format.contains("After this event, the error counter is {}")) {
                            Object arg1 = args[1];
                            if (arg1 instanceof Number) {
                                loggedCounts.add(((Number) arg1).longValue());
                            }
                        }
                    }
                    return null;
                }
        );

        check.log = mockLogger;
        check.init();

        int threadCount = 20;
        int callsPerThread = 500;
        int totalCalls = threadCount * callsPerThread;

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
        Assertions.assertEquals(totalCalls, loggedCounts.size(),
                "Every suspect() call must log a distinct counter value without duplicates");

        HealthCheckResponse response = check.call();
        Assertions.assertEquals("PersistenceExceptionLivenessCheck", response.getName());
        Assertions.assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
        Assertions.assertEquals((long) totalCalls, response.getData().get().get("errorCount"));
    }

    @Test
    void testResponseErrorLivenessCheckDistinctLoggedCountsUnderConcurrency() throws Exception {
        ResponseErrorLivenessCheck check = new ResponseErrorLivenessCheck();
        check.configErrorThreshold = 10000;
        check.configCounterResetWindowDurationSec = 60;
        check.configStatusResetWindowDurationSec = 300;
        check.disableLogging = false;

        Set<Long> loggedCounts = Collections.newSetFromMap(new ConcurrentHashMap<>());
        Logger mockLogger = (Logger) java.lang.reflect.Proxy.newProxyInstance(
                Logger.class.getClassLoader(),
                new Class<?>[]{Logger.class},
                (proxy, method, args) -> {
                    if ("info".equals(method.getName()) && args != null && args.length >= 2) {
                        String format = String.valueOf(args[0]);
                        if (format.contains("After this event, the error counter is {}")) {
                            Object arg1 = args[1];
                            if (arg1 instanceof Number) {
                                loggedCounts.add(((Number) arg1).longValue());
                            }
                        }
                    }
                    return null;
                }
        );

        check.log = mockLogger;
        check.init();

        int threadCount = 20;
        int callsPerThread = 500;
        int totalCalls = threadCount * callsPerThread;

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
                            check.suspect("simulated response error " + j);
                        } else {
                            check.suspectWithException(new RuntimeException("simulated response exception " + j));
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
        Assertions.assertEquals(totalCalls, loggedCounts.size(),
                "Every suspect() call must log a distinct counter value without duplicates");

        HealthCheckResponse response = check.call();
        Assertions.assertEquals("ResponseErrorLivenessCheck", response.getName());
        Assertions.assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
        Assertions.assertEquals((long) totalCalls, response.getData().get().get("errorCount"));
    }

    @Test
    void testResponseErrorLivenessCheckThreshold() {
        ResponseErrorLivenessCheck check = new ResponseErrorLivenessCheck();
        check.log = Mockito.mock(Logger.class);
        check.configErrorThreshold = 10;
        check.configCounterResetWindowDurationSec = 60;
        check.configStatusResetWindowDurationSec = 300;
        check.disableLogging = true;
        check.init();

        for (int i = 0; i < 11; i++) {
            check.suspect("error " + i);
        }

        HealthCheckResponse response = check.call();
        Assertions.assertEquals(HealthCheckResponse.Status.DOWN, response.getStatus());
        Assertions.assertEquals(11L, response.getData().get().get("errorCount"));
    }
}
