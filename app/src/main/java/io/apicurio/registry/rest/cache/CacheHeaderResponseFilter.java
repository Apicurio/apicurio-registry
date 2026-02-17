package io.apicurio.registry.rest.cache;

import io.apicurio.registry.rest.cache.HttpCaching.ResponseAdapter;
import io.apicurio.registry.util.Priorities;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.apicurio.registry.rest.cache.HttpCaching.caching;

/**
 * JAX-RS response filter that adds HTTP cache headers to responses
 * when a cache strategy was set by EntityIdContentCacheInterceptor.
 * <p>
 * This filter runs after the resource method completes successfully
 * (i.e., when 304 was not returned).
 */
@Provider
@Priority(Priorities.RequestResponseFilters.CACHE)
public class CacheHeaderResponseFilter implements ContainerResponseFilter {

    private static final Logger log = LoggerFactory.getLogger(CacheHeaderResponseFilter.class);

    @Inject
    ResponseCacheContext cacheContext;

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        var strategy = cacheContext.getStrategy();
        if (strategy != null) {
            caching(strategy).applyCacheHeaders(new ResponseAdapter() {
                @Override
                public int getResponseStatus() {
                    return responseContext.getStatus();
                }

                @Override
                public void setResponseHeader(String key, Object value) {
                    responseContext.getHeaders().putSingle(key, value);
                }
            });
        } else {
            log.debug("No cache strategy found in context, skipping cache header application.");
        }
    }
}
