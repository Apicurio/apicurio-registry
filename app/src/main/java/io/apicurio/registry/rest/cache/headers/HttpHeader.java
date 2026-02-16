package io.apicurio.registry.rest.cache.headers;

import io.apicurio.registry.rest.cache.HttpCaching.ResponseAdapter;
import jakarta.ws.rs.core.MultivaluedMap;

public interface HttpHeader {

    String key();

    Object value();

    default void apply(ResponseAdapter adapter) {
        adapter.setResponseHeader(key(), value());
    }

    default void apply(MultivaluedMap<String, Object> headers) {
        headers.putSingle(key(), value());
    }
}
