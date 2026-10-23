package io.quarkus.test.common;

import org.jboss.jandex.Index;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class TestClassIndexerTest {

    @Test
    public void testConcurrentReadWriteDoesNotCorruptOrThrow(@TempDir Path tempDir) throws Exception {
        // Prepare dummy class files in test directory
        Path classFile = tempDir.resolve("TestClassIndexerTest.class");
        try (var in = TestClassIndexerTest.class.getResourceAsStream("/io/quarkus/test/common/TestClassIndexerTest.class")) {
            if (in != null) {
                Files.copy(in, classFile);
            }
        }

        Index index = TestClassIndexer.indexTestClasses(tempDir);
        Assertions.assertNotNull(index);

        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicBoolean running = new AtomicBoolean(true);
        List<Future<Void>> futures = new ArrayList<>();

        try {
            // Half writers, half readers
            for (int i = 0; i < threadCount / 2; i++) {
                futures.add(executor.submit((Callable<Void>) () -> {
                    while (running.get()) {
                        TestClassIndexer.writeIndex(index, tempDir, TestClassIndexerTest.class);
                        Thread.yield();
                    }
                    return null;
                }));
            }

            for (int i = 0; i < threadCount / 2; i++) {
                futures.add(executor.submit((Callable<Void>) () -> {
                    while (running.get()) {
                        Index read = TestClassIndexer.readIndex(tempDir, TestClassIndexerTest.class);
                        Assertions.assertNotNull(read);
                    }
                    return null;
                }));
            }

            // Run concurrent stress for 1.5 seconds
            Thread.sleep(1500);
            running.set(false);

            for (Future<Void> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
        } finally {
            running.set(false);
            executor.shutdownNow();
        }

        // Final verification: read index once more
        Index finalIndex = TestClassIndexer.readIndex(tempDir, TestClassIndexerTest.class);
        Assertions.assertNotNull(finalIndex);
    }
}
