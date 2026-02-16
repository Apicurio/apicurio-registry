package io.apicurio.registry.rest.cache.headers;

import io.apicurio.registry.rest.cache.Cacheability;
import lombok.AllArgsConstructor;
import lombok.Builder;

import static lombok.AccessLevel.PRIVATE;

/**
 * X-Cache-Cacheability header indicates the evaluated cacheability level for the response.
 * This is an informational header useful for development and troubleshooting.
 * <p>
 * Example: X-Cache-Cacheability: HIGH
 */
@AllArgsConstructor(access = PRIVATE)
@Builder
public class XCacheCacheabilityHttpHeader implements HttpHeader {

    private final Cacheability cacheability;

    @Override
    public String key() {
        return "X-Cache-Cacheability";
    }

    @Override
    public Object value() {
        return cacheability.name();
    }
}
