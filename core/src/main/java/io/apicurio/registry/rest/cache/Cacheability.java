package io.apicurio.registry.rest.cache;

import java.util.Arrays;
import java.util.Objects;

import static java.util.Arrays.stream;
import static java.util.Comparator.comparingInt;

/**
 * Enum representing the cacheability of a resource.
 * Cacheability is a property of a specific resource (based on the individual request), not necessarily an entire endpoint.
 * <p>
 * IMPORTANT: Cacheability can change based on Registry configuration, and if cacheability of previously
 * highly cacheable resource is downgraded to moderately cacheable (or similar),
 * the cache should be purged to avoid serving stale content.
 * <p>
 * High-quality ETags are those <em>that do not change unnecessarily</em>.
 * We still do best-effort ETag generation for all resources.
 */
public enum Cacheability {

    /**
     * Resource is very unlikely to change.
     * High-quality ETags are available.
     */
    HIGH(3),
    /**
     * Resource might change often,
     * but high-quality ETags are still available.
     */
    MODERATE(2),
    /**
     * Resource might change often,
     * and high-quality ETags are not available.
     */
    LOW(1),
    /**
     * Resource is not cacheable.
     * ETags are not available or not useful.
     */
    NONE(0);

    private final int level;

    Cacheability(int level) {
        this.level = level;
    }

    private int getLevel() {
        return level;
    }

    /**
     * Returns the lowest Cacheability from the given values.
     * If no values are provided, throws IllegalArgumentException.
     */
    public static Cacheability min(Cacheability... cacheabilities) {
        if (cacheabilities == null) {
            throw new IllegalArgumentException("Nothing to compare: cacheabilities is null");
        }
        return stream(cacheabilities)
                .filter(Objects::nonNull)
                .min(comparingInt(Cacheability::getLevel))
                .orElseThrow(() -> new IllegalArgumentException("Nothing to compare: " + Arrays.toString(cacheabilities)));
    }
}
