package io.apicurio.registry.rest.cache.headers;

import jakarta.ws.rs.core.HttpHeaders;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Singular;

import java.util.List;

@AllArgsConstructor
@Builder
public class VaryHttpHeader implements HttpHeader {

    @Singular
    private final List<String> headers;

    @Override
    public String key() {
        return HttpHeaders.VARY;
    }

    @Override
    public String value() {
        return String.join(",", headers);
    }
}
