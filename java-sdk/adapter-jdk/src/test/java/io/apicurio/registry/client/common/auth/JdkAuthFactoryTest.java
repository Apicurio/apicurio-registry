package io.apicurio.registry.client.common.auth;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link JdkAuthFactory}.
 */
class JdkAuthFactoryTest {

    @Test
    void testBuildBasicAuthHeaderValue() {
        String header = JdkAuthFactory.buildBasicAuthHeaderValue("user", "password");

        assertNotNull(header);
        assertTrue(header.startsWith("Basic "));

        // Decode and verify
        String encoded = header.substring("Basic ".length());
        String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        assertEquals("user:password", decoded);
    }

    @Test
    void testBuildBasicAuthHeaderValueWithSpecialCharacters() {
        String header = JdkAuthFactory.buildBasicAuthHeaderValue("user@domain.com", "p@ss:word!");

        assertNotNull(header);
        assertTrue(header.startsWith("Basic "));

        String encoded = header.substring("Basic ".length());
        String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        assertEquals("user@domain.com:p@ss:word!", decoded);
    }

    @Test
    void testBuildBasicAuthHeaderValueWithEmptyPassword() {
        String header = JdkAuthFactory.buildBasicAuthHeaderValue("user", "");

        assertNotNull(header);
        assertTrue(header.startsWith("Basic "));

        String encoded = header.substring("Basic ".length());
        String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        assertEquals("user:", decoded);
    }

    @Test
    void testBuildBasicAuthHeaderValueWithUnicodeCharacters() {
        String header = JdkAuthFactory.buildBasicAuthHeaderValue("用户", "密码");

        assertNotNull(header);
        assertTrue(header.startsWith("Basic "));

        String encoded = header.substring("Basic ".length());
        String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        assertEquals("用户:密码", decoded);
    }

    @Test
    void testBuildProxyAuthenticator() {
        var authenticator = JdkAuthFactory.buildProxyAuthenticator("proxyUser", "proxyPass");
        assertNotNull(authenticator);
    }

    @Test
    void testBuildOAuth2TokenProviderCreation() {
        HttpClient httpClient = HttpClient.newBuilder().build();

        JdkAuthFactory.TokenProvider tokenProvider = JdkAuthFactory.buildOAuth2TokenProvider(
                httpClient,
                "https://auth.example.com/oauth/token",
                "client-id",
                "client-secret",
                "openid profile"
        );

        assertNotNull(tokenProvider);
        assertTrue(tokenProvider instanceof JdkAuthFactory.OAuth2TokenProvider);
    }

    @Test
    void testOAuth2TokenProviderWithNullScope() {
        HttpClient httpClient = HttpClient.newBuilder().build();

        JdkAuthFactory.TokenProvider tokenProvider = JdkAuthFactory.buildOAuth2TokenProvider(
                httpClient,
                "https://auth.example.com/oauth/token",
                "client-id",
                "client-secret",
                null
        );

        assertNotNull(tokenProvider);
    }

    @Test
    void testOAuth2TokenProviderGetTokenFailsWithInvalidEndpoint() {
        HttpClient httpClient = HttpClient.newBuilder().build();

        JdkAuthFactory.TokenProvider tokenProvider = JdkAuthFactory.buildOAuth2TokenProvider(
                httpClient,
                "https://invalid.endpoint.local/oauth/token",
                "client-id",
                "client-secret",
                null
        );

        // Should throw IOException when trying to get token from invalid endpoint
        assertThrows(IOException.class, tokenProvider::getToken);
    }
}
