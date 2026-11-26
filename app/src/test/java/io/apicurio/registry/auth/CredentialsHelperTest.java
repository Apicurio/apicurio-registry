package io.apicurio.registry.auth;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CredentialsHelperTest {

    private static RoutingContext contextWithAuthorization(String... values) {
        MultiMap headers = MultiMap.caseInsensitiveMultiMap();
        for (String value : values) {
            headers.add(HttpHeaderNames.AUTHORIZATION, value);
        }
        HttpServerRequest request = mock(HttpServerRequest.class);
        when(request.headers()).thenReturn(headers);
        RoutingContext context = mock(RoutingContext.class);
        when(context.request()).thenReturn(request);
        return context;
    }

    private static String basic(String plain) {
        return "Basic " + Base64.getEncoder().encodeToString(plain.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void malformedBase64ReturnsNoCredentials() {
        assertNull(CredentialsHelper.extractCredentialsFromContext(contextWithAuthorization("Basic !!!notbase64")));
    }

    @Test
    void malformedHeaderIsSkippedInFavourOfAValidOne() {
        Pair<String, String> credentials = CredentialsHelper.extractCredentialsFromContext(
                contextWithAuthorization("Basic !!!notbase64", basic("client:secret")));
        assertEquals("client", credentials.getLeft());
        assertEquals("secret", credentials.getRight());
    }

    @Test
    void wellFormedHeaderIsDecoded() {
        Pair<String, String> credentials = CredentialsHelper
                .extractCredentialsFromContext(contextWithAuthorization(basic("user:p:w")));
        assertEquals("user", credentials.getLeft());
        assertEquals("p:w", credentials.getRight());
    }
}
