package io.apicurio.registry.rest.cache.headers;

import jakarta.ws.rs.core.HttpHeaders;
import joptsimple.internal.Strings;
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
    public Object value() {
        // TODO: Authentication
        return Strings.join(new String[]{HttpHeaders.ACCEPT, HttpHeaders.ACCEPT_ENCODING, HttpHeaders.AUTHORIZATION}, ",");
    }
}
