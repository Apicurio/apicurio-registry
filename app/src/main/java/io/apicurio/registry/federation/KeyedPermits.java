package io.apicurio.registry.federation;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Counts what is in progress for each key, and refuses to start more than a maximum for one key.
 * A key is forgotten once nothing is in progress for it.
 */
final class KeyedPermits {

    private final ConcurrentMap<String, Integer> held = new ConcurrentHashMap<>();

    /**
     * @return whether a permit was taken; if so, it must be given back with {@link #release(String)}
     */
    boolean tryAcquire(String key, int max) {
        boolean[] acquired = new boolean[1];
        held.compute(key, (ignored, count) -> {
            int current = count == null ? 0 : count;
            if (current >= max) {
                return count;
            }
            acquired[0] = true;
            return current + 1;
        });
        return acquired[0];
    }

    void release(String key) {
        held.computeIfPresent(key, (ignored, count) -> count <= 1 ? null : count - 1);
    }

    int held(String key) {
        return held.getOrDefault(key, 0);
    }
}
