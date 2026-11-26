package io.apicurio.registry.agents.a2a.signature;

import io.apicurio.registry.http.OutboundConfig;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwksFetcherTest {

    private static JwksFetcher fetcher(boolean insecureHttp, boolean loopback, int maxBytes) {
        AgentCardSignatureConfig config = new AgentCardSignatureConfig();
        config.fetchTimeoutSeconds = 2;
        config.fetchMaxSizeBytes = maxBytes;
        OutboundConfig outbound = mock(OutboundConfig.class);
        when(outbound.isInsecureHttpEnabled()).thenReturn(insecureHttp);
        when(outbound.isLoopbackEnabled()).thenReturn(loopback);
        JwksFetcher fetcher = new JwksFetcher();
        fetcher.config = config;
        fetcher.outboundConfig = outbound;
        fetcher.init();
        return fetcher;
    }

    private static String servedPath(String body) {
        String path = "/fetcher/" + UUID.randomUUID();
        JwksStub.get().serve(path, body);
        return path;
    }

    @Test
    void fetchesAKeySet() throws Exception {
        String path = servedPath("{\"keys\":[]}");

        assertEquals("{\"keys\":[]}", fetcher(true, true, 1024).fetch(JwksStub.url(path)));
        assertEquals(1, JwksStub.get().requestCount(path));
    }

    @Test
    void refusesALargerResponse() {
        String path = servedPath("{\"keys\":[]}");

        JwksFetcher.JwksFetchException error = assertThrows(JwksFetcher.JwksFetchException.class,
                () -> fetcher(true, true, 5).fetch(JwksStub.url(path)));

        assertEquals("response larger than 5 bytes", error.getMessage());
    }

    @Test
    void refusesAnErrorStatus() {
        JwksFetcher.JwksFetchException error = assertThrows(JwksFetcher.JwksFetchException.class,
                () -> fetcher(true, true, 1024).fetch(JwksStub.url("/fetcher/missing-" + UUID.randomUUID())));

        assertEquals("HTTP status 404", error.getMessage());
    }

    @Test
    void refusesPlainHttpUnlessAllowed() {
        String path = servedPath("{\"keys\":[]}");

        JwksFetcher.JwksFetchException error = assertThrows(JwksFetcher.JwksFetchException.class,
                () -> fetcher(false, true, 1024).fetch(JwksStub.url(path)));

        assertEquals("must use https", error.getMessage());
        assertEquals(0, JwksStub.get().requestCount(path));
    }

    @Test
    void refusesLoopbackUnlessAllowed() {
        String path = servedPath("{\"keys\":[]}");

        JwksFetcher.JwksFetchException error = assertThrows(JwksFetcher.JwksFetchException.class,
                () -> fetcher(true, false, 1024).fetch(JwksStub.url(path)));

        assertEquals("host is a loopback address", error.getMessage());
        assertEquals(0, JwksStub.get().requestCount(path));
    }

    @Test
    void refusesCloudMetadataAndUserInfo() {
        JwksFetcher fetcher = fetcher(true, true, 1024);

        assertEquals("host is a link-local address", assertThrows(JwksFetcher.JwksFetchException.class,
                () -> fetcher.fetch("http://169.254.169.254/jwks")).getMessage());
        assertEquals("must not contain user information", assertThrows(JwksFetcher.JwksFetchException.class,
                () -> fetcher.fetch("https://user:pass@keys.example.com/jwks")).getMessage());
    }
}
