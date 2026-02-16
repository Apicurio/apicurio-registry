package io.apicurio.registry.rest.cache;

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
    HIGH,
    /**
     * Resource might change often,
     * but high-quality ETags are still available.
     */
    MODERATE,
    /**
     * Resource might change often,
     * and high-quality ETags are not available.
     */
    LOW,
    /**
     * Resource is not cacheable.
     * ETags are not available or not useful.
     */
    NONE;
}
