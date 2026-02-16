package io.apicurio.registry.rest.cache.headers;

import jakarta.ws.rs.core.HttpHeaders;
import lombok.AllArgsConstructor;
import lombok.Builder;

@AllArgsConstructor
@Builder
public class VaryHttpHeader implements HttpHeader {

    @Override
    public String key() {
        return HttpHeaders.VARY;
    }

    @Override
    public String value() {
        // TODO: Analyze how to handle authentication better.
        return String.join(",", HttpHeaders.ACCEPT, HttpHeaders.ACCEPT_ENCODING, HttpHeaders.AUTHORIZATION);
    }
}
