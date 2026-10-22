package io.apicurio.registry.rest.cache;

import io.apicurio.registry.rest.cache.strategy.CacheStrategy;
import jakarta.enterprise.context.RequestScoped;
import lombok.Getter;
import lombok.Setter;

/**
 * Request-scoped context for storing cache strategy information.
 * Used by EntityIdContentCacheInterceptor to communicate with CacheHeaderResponseFilter.
 */
@RequestScoped
public class ResponseCacheContext {

    @Getter
    @Setter
    private CacheStrategy strategy;
}
