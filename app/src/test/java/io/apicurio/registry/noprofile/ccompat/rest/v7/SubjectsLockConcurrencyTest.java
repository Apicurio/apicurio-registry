package io.apicurio.registry.noprofile.ccompat.rest.v7;

import com.google.common.util.concurrent.Striped;
import io.apicurio.registry.ccompat.rest.v7.impl.SubjectsResourceImpl;
import io.apicurio.registry.model.GA;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubjectsLockConcurrencyTest {

    @SuppressWarnings("unchecked")
    @Test
    void testActiveLockPreservesMutualExclusionUnderConcurrency() throws Exception {
        SubjectsResourceImpl resource = new SubjectsResourceImpl();
        Field field = SubjectsResourceImpl.class.getDeclaredField("subjectLocks");
        field.setAccessible(true);
        Striped<Lock> subjectLocks = (Striped<Lock>) field.get(resource);

        GA ga = new GA("test-group", "test-subject");

        Lock lockA = subjectLocks.get(ga);
        Lock lockB = subjectLocks.get(ga);
        assertSame(lockA, lockB, "Lookups for the same subject must return the exact same lock");

        CountDownLatch threadAHoldingLock = new CountDownLatch(1);
        CountDownLatch releaseThreadA = new CountDownLatch(1);
        AtomicBoolean threadBAcquiredLockWhileAHeld = new AtomicBoolean(false);
        AtomicBoolean threadBAcquiredLockAfterAReleased = new AtomicBoolean(false);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> futureA = executor.submit(() -> {
                lockA.lock();
                try {
                    threadAHoldingLock.countDown();
                    assertTrue(releaseThreadA.await(5, TimeUnit.SECONDS), "Timed out waiting to release Thread A");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    lockA.unlock();
                }
            });

            Future<?> futureB = executor.submit(() -> {
                try {
                    assertTrue(threadAHoldingLock.await(5, TimeUnit.SECONDS), "Timed out waiting for Thread A to hold lock");

                    // Try to acquire lock for the same subject while Thread A is holding it
                    boolean acquired = lockB.tryLock();
                    if (acquired) {
                        try {
                            threadBAcquiredLockWhileAHeld.set(true);
                        } finally {
                            lockB.unlock();
                        }
                    }

                    // Release Thread A and now acquire with a wait
                    releaseThreadA.countDown();
                    if (lockB.tryLock(5, TimeUnit.SECONDS)) {
                        try {
                            threadBAcquiredLockAfterAReleased.set(true);
                        } finally {
                            lockB.unlock();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            futureA.get(10, TimeUnit.SECONDS);
            futureB.get(10, TimeUnit.SECONDS);

            assertFalse(threadBAcquiredLockWhileAHeld.get(), "Thread B must not acquire the lock while Thread A holds it");
            assertTrue(threadBAcquiredLockAfterAReleased.get(), "Thread B must acquire the lock once Thread A releases it");
        } finally {
            executor.shutdownNow();
        }
    }
}
