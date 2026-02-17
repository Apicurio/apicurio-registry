package io.apicurio.registry.rest.cache;

import io.apicurio.registry.rest.cache.headers.ETagHttpHeader;
import io.apicurio.registry.rest.cache.headers.SurrogateControlHttpHeader;
import io.apicurio.registry.rest.cache.headers.VaryHttpHeader;
import io.apicurio.registry.rest.cache.headers.XCacheCacheabilityHttpHeader;
import io.apicurio.registry.rest.cache.strategy.CacheStrategy;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.ws.rs.core.HttpHeaders;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.apicurio.registry.utils.TimeUtils.isPositive;
import static java.util.Optional.ofNullable;

public class HttpCaching {

    private static final Logger log = LoggerFactory.getLogger(HttpCaching.class);

    private final CacheStrategy strategy;

    public static HttpCaching caching(CacheStrategy strategy) {
        return new HttpCaching(strategy);
    }

    private HttpCaching(CacheStrategy strategy) {
        this.strategy = strategy;
    }

    /**
     * IMPORTANT: We can't dynamically access a bean using the CDI API, unless there is an injection point present somewhere,
     * or the bean is one of the roots of the CDI graph.
     * <p>
     * My guess is that this is a limitation of how Quarkus processes the CDI graph at build time.
     */
    private static <T> T getBean(Class<T> beanClass) { // TODO: Move to a utility class?
        var instance = CDI.current().select(beanClass);
        if (!instance.isResolvable()) {
            throw new IllegalStateException("CDI context does not contain a single instance of '" + beanClass.getCanonicalName() + "'. " +
                    "Found: " + instance.stream().map(i -> i.getClass().getCanonicalName()).toList());
        }
        return instance.get();
    }

    /**
     * Variant of {@link #getBean(Class)} that returns null if the bean is not resolvable instead of throwing an exception.
     */
    public static <T> T getBeanOrNull(Class<T> beanClass) { // TODO: Move to a utility class?
        var instance = CDI.current().select(beanClass);
        if (!instance.isResolvable()) {
            log.debug("CDI context does not contain a single instance of '{}'. Found: {}", beanClass.getCanonicalName(),
                    instance.stream().map(i -> i.getClass().getCanonicalName()).toList());
            return null;
        }
        return instance.get();
    }

    /**
     * Prepares HTTP caching for the current request.
     * This method should be called from REST endpoints before building the response.
     * It evaluates the cache strategy, checks for conditional requests (If-None-Match),
     * and stores the strategy for the response filter to apply cache headers.
     * <p>
     * If the request matches the cached ETag, this method throws {@link CacheNotModifiedException}
     * which will be mapped to a 304 Not Modified response.
     */
    public void prepare() {
        var config = getBean(HttpCachingConfig.class);
        if (!config.isCachingEnabled()) {
            return;
        }
        strategy.evaluate();
        getBean(ResponseCacheContext.class).setStrategy(strategy);
        checkETag();
    }

    private void checkETag() {
        var httpHeaders = ofNullable(ResteasyProviderFactory.getInstance().getContextData(HttpHeaders.class))
                .orElseThrow(() -> new IllegalStateException("This method must be executed in the context of a JAX-RS request."));
        String ifNoneMatch = httpHeaders.getHeaderString(HttpHeaders.IF_NONE_MATCH);
        if (ifNoneMatch != null) {
            var etag = strategy.getETagBuilder().build();
            var etagHeader = ETagHttpHeader.builder().etag(etag).build();
            if (etagHeader.matches(ifNoneMatch)) {
                throw new CacheNotModifiedException();
            }
        }
    }

    void applyCacheHeaders(ResponseAdapter adapter) {
        var config = getBean(HttpCachingConfig.class);
        if (!config.isCachingEnabled()) {
            log.debug("HTTP caching is disabled, skipping cache headers.");
            return;
        }
        int status = adapter.getResponseStatus();
        if (status >= 200 && status < 300) {
            // Only add cache headers for 2xx responses:

            // Add ETag header
            var etagBuilder = strategy.getETagBuilder();
            var etag = config.getOpaqueETagsEnabled() ? etagBuilder.buildHashed() : etagBuilder.build();
            ETagHttpHeader.builder()
                    .etag(etag)
                    .build()
                    .apply(adapter);

            // Add X-Cache-Cacheability header if extra headers are enabled
            if (config.isExtraHeadersEnabled()) {
                XCacheCacheabilityHttpHeader.builder()
                        .cacheability(strategy.getCacheability())
                        .build()
                        .apply(adapter);
            }

            switch (strategy.getCacheability()) {
                case HIGH -> {
                    if (isPositive(config.getHighExpiration())) {
                        log.debug("Applying high cacheability strategy {}.", strategy.description());

                        SurrogateControlHttpHeader.builder()
                                .immutable(true)
                                .expiration(config.getHighExpiration())
                                .build()
                                .apply(adapter);

                        VaryHttpHeader.builder().build().apply(adapter);
                    } else {
                        log.debug("Skipping adding cache headers for a high cacheability response because " +
                                        "`apicurio.http-caching.high-cacheability.max-age-seconds={}`.",
                                config.getHighExpiration().getSeconds());
                    }
                }
                case MODERATE -> {
                    if (isPositive(config.getModerateExpiration())) {
                        log.debug("Applying moderate cacheability strategy {}.", strategy.description());

                        SurrogateControlHttpHeader.builder()
                                .expiration(config.getModerateExpiration())
                                .build()
                                .apply(adapter);

                        VaryHttpHeader.builder().build().apply(adapter);
                    } else {
                        log.debug("Skipping adding cache headers for a moderate cacheability response because " +
                                        "`apicurio.http-caching.moderate-cacheability.max-age-seconds={}`.",
                                config.getModerateExpiration().getSeconds());
                    }
                }
                case LOW -> {
                    if (isPositive(config.getLowExpiration())) {
                        log.debug("Applying low cacheability strategy {}.", strategy.description());

                        SurrogateControlHttpHeader.builder()
                                .expiration(config.getLowExpiration())
                                .build()
                                .apply(adapter);

                        VaryHttpHeader.builder().build().apply(adapter);
                    } else {
                        log.debug("Skipping adding cache headers for a low cacheability response because " +
                                        "`apicurio.http-caching.low-cacheability.max-age-seconds={}`.",
                                config.getLowExpiration().getSeconds());
                    }
                }
                case NONE -> {
                    log.debug("Not applying cache headers as response is not cacheable.");
                    // TODO: Other headers?
                }
            }
        } else {
            log.debug("Skipping adding cache headers as the response status {} and not 2xx.", status);
        }
    }

    public interface ResponseAdapter {

        int getResponseStatus();

        void setResponseHeader(String key, Object value);
    }
}
