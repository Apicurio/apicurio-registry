package io.apicurio.registry.rest.cache.strategy;

import io.apicurio.registry.rest.RestConfig;
import io.apicurio.registry.rest.cache.Cacheability;
import io.apicurio.registry.rest.cache.HttpCachingConfig;
import io.apicurio.registry.rest.cache.etag.ETagBuilder;

import static io.apicurio.registry.rest.cache.HttpCaching.getBeanOrNull;
import static java.util.Optional.ofNullable;

/**
 * Cache strategy encapsulated logic that makes decisions about how to handle HTTP caching for a particular request.
 * Different endpoints require different parameters and logic to decide how to handle caching.
 */
public abstract class CacheStrategy {

    protected ETagBuilder eTagBuilder;
    protected Cacheability cacheability;

    public ETagBuilder getETagBuilder() {
        if (eTagBuilder == null) {
            throw new IllegalStateException("Cache strategy " + description() + " has not been evaluated yet. " +
                    "Call evaluate() first.");
        }
        return eTagBuilder;
    }

    public Cacheability getCacheability() {
        if (cacheability == null) {
            throw new IllegalStateException("Cache strategy " + description() + " has not been evaluated yet. " +
                    "Call evaluate() first.");
        }
        return cacheability;
    }

    public abstract void evaluate();

    public abstract String description();

    static boolean isHigherQualityEtagEnabled() {
        return ofNullable(getBeanOrNull(HttpCachingConfig.class))
                .map(HttpCachingConfig::isHigherQualityETagsEnabled)
                .orElse(false);
    }

    static boolean isVersionMutabilityEnabled() {
        return ofNullable(getBeanOrNull(RestConfig.class))
                .map(RestConfig::isArtifactVersionMutabilityEnabled)
                .orElse(false);
    }
}
