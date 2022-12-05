package io.apicurio.registry.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

class CheckPeriodCacheTest {

    @Test
    void testEviction() throws InterruptedException {
        for (int attempt = 0; attempt < 5; attempt++) {

            var cache = new CheckPeriodCache<Integer, Integer>(Duration.ofSeconds(1), 10);
            var t1 = new Thread(() -> {
                for (int i = 1; i <= 1000; i++) {
                    cache.compute(i, k -> k);
                }
                Assertions.assertEquals(1000, cache.size());
                Assertions.assertEquals(cache.getInternal().size(), cache.size());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                for (int i = 1001; i <= 1010; i++) {
                    cache.compute(i, k -> k);
                }
            });
            var t2 = new Thread(() -> {
                for (int i = 1; i <= 1000; i++) {
                    cache.compute(i, k -> k);
                }
                Assertions.assertEquals(1000, cache.size());
                Assertions.assertEquals(cache.getInternal().size(), cache.size());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                for (int i = 1001; i <= 1010; i++) {
                    cache.compute(i, k -> k);
                }
            });
            t1.start();
            t2.start();
            t1.join();
            t2.join();

            Assertions.assertEquals(10, cache.size());
            var actual = cache.getInternal().values().stream().map(c -> c.value).collect(Collectors.toSet());
            var expected = IntStream.range(1001, 1011).boxed().collect(Collectors.toSet());
            Assertions.assertEquals(expected, actual);
        }
    }
}
