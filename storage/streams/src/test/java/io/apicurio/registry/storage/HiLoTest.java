package io.apicurio.registry.storage;

import io.apicurio.registry.streams.utils.LongGenerator;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * @author Ales Justin
 */
public class HiLoTest {
    @Test
    public void testSmoke() {
        AtomicLong hiCounter = new AtomicLong();
        LongGenerator hiGen = hiCounter::getAndIncrement;
        LongGenerator hiLoGen = new LongGenerator.HiLo(hiGen, 4);

        Map<Long, Long> result = IntStream
            .range(0, 1_600_000)
            .parallel()
            .mapToObj(i -> hiLoGen.getNext())
            .collect(Collectors.toMap(Function.identity(), Function.identity()));

        if (result.size() != 1_600_000) {
            throw new AssertionError("result.size() expected: " + 1_600_000 + ", got: " + result.size());
        }
        if (hiCounter.get() != 100_000) {
            throw new AssertionError("hiCounter expected: " + 100_000 + ", got: " + hiCounter.get());
        }
    }
}
