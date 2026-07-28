package io.apicurio.registry.client.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistryUrlValidationTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com/apis/registry/v3",
            "https://registry.example.com:8443/apis/registry/v3",
            "http://localhost:8080/apis/registry/v3",
            "http://example.com/apis/registry/v3",
    })
    void testValidUrlsAreAccepted(String url) {
        var options = RegistryClientOptions.create().rawRegistryUrl(url);
        try {
            RegistryClientRequestAdapterFactory.createRequestAdapter(options, Version.V3);
        } catch (IllegalArgumentException ex) {
            throw new AssertionError("URL '" + url + "' should be accepted but was rejected: " + ex.getMessage(), ex);
        } catch (Exception ignored) {
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ftp://example.com/apis/registry/v3",
            "ws://example.com/apis/registry/v3",
    })
    void testUnsupportedSchemesAreRejected(String url) {
        var options = RegistryClientOptions.create().rawRegistryUrl(url);
        var ex = assertThrows(IllegalArgumentException.class, () ->
                RegistryClientRequestAdapterFactory.createRequestAdapter(options, Version.V3));
        assertTrue(ex.getMessage().contains("unsupported scheme"), ex.getMessage());
    }

    @Test
    void testMissingSchemeIsRejected() {
        var options = RegistryClientOptions.create().rawRegistryUrl("example.com/apis/registry/v3");
        var ex = assertThrows(IllegalArgumentException.class, () ->
                RegistryClientRequestAdapterFactory.createRequestAdapter(options, Version.V3));
        assertTrue(ex.getMessage().contains("not well-formed"), ex.getMessage());
    }

    @Test
    void testNullUrlIsRejected() {
        var options = RegistryClientOptions.create();
        assertThrows(IllegalArgumentException.class, () ->
                RegistryClientRequestAdapterFactory.createRequestAdapter(options, Version.V3));
    }

    @Test
    void testBlankUrlIsRejected() {
        var options = RegistryClientOptions.create("   ");
        assertThrows(IllegalArgumentException.class, () ->
                RegistryClientRequestAdapterFactory.createRequestAdapter(options, Version.V3));
    }

    @Test
    void testInvalidUriSyntaxIsRejected() {
        var options = RegistryClientOptions.create().rawRegistryUrl("http://exam ple.com/apis/registry/v3");
        var ex = assertThrows(IllegalArgumentException.class, () ->
                RegistryClientRequestAdapterFactory.createRequestAdapter(options, Version.V3));
        assertTrue(ex.getMessage().contains("not well-formed"), ex.getMessage());
    }
}
