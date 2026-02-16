package io.apicurio.registry.rest.cache;

import io.apicurio.registry.rest.cache.strategy.CacheStrategy;
import jakarta.enterprise.context.RequestScoped;
import lombok.Setter;

import java.util.Optional;

import static java.util.Optional.ofNullable;

/**
 * Request-scoped context for storing cache strategy information.
 * Used by EntityIdContentCacheInterceptor to communicate with CacheHeaderResponseFilter.
 */
@RequestScoped
@Setter
public class ResponseCacheContext {

    private CacheStrategy strategy;

    public Optional<CacheStrategy> getStrategy() {
        return ofNullable(strategy);
    }
}
