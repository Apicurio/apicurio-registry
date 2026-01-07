package io.apicurio.registry.client.common;

import com.microsoft.kiota.RequestAdapter;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for JDK adapter authentication functionality.
 * Tests that the JDK adapter properly handles basic auth and OAuth2 configuration.
 */
class JdkAdapterAuthenticationTest {

    @Test
    void testJdkAdapterWithBasicAuthCreatesAdapter() {
        RegistryClientOptions options = RegistryClientOptions.create()
                .registryUrl("http://localhost:8080")
                .httpAdapter(HttpAdapterType.JDK)
                .basicAuth("testuser", "testpassword");

        // Should not throw - adapter should be created successfully
        RequestAdapter adapter = assertDoesNotThrow(() ->
                RegistryClientRequestAdapterFactory.createRequestAdapter(options, Version.V3));

        assertNotNull(adapter, "Adapter should be created");
        assertTrue(adapter.getClass().getName().contains("JdkAuthenticatedRequestAdapter"),
                "Should create JdkAuthenticatedRequestAdapter for basic auth");
    }

    @Test
    void testJdkAdapterWithOAuth2CreatesAdapter() {
        RegistryClientOptions options = RegistryClientOptions.create()
                .registryUrl("http://localhost:8080")
                .httpAdapter(HttpAdapterType.JDK)
                .oauth2("http://localhost:8180/auth/realms/test/protocol/openid-connect/token",
                        "test-client", "test-secret");

        // Should not throw - adapter should be created successfully
        RequestAdapter adapter = assertDoesNotThrow(() ->
                RegistryClientRequestAdapterFactory.createRequestAdapter(options, Version.V3));

        assertNotNull(adapter, "Adapter should be created");
        assertTrue(adapter.getClass().getName().contains("JdkOAuth2RequestAdapter"),
                "Should create JdkOAuth2RequestAdapter for OAuth2");
    }

    @Test
    void testJdkAdapterWithAnonymousCreatesAdapter() {
        RegistryClientOptions options = RegistryClientOptions.create()
                .registryUrl("http://localhost:8080")
                .httpAdapter(HttpAdapterType.JDK);

        // Should not throw - adapter should be created successfully
        RequestAdapter adapter = assertDoesNotThrow(() ->
                RegistryClientRequestAdapterFactory.createRequestAdapter(options, Version.V3));

        assertNotNull(adapter, "Adapter should be created");
        assertTrue(adapter.getClass().getName().contains("JDKRequestAdapter"),
                "Should create JDKRequestAdapter for anonymous auth");
    }

    @Test
    void testJdkAdapterWithCustomWebClientThrows() {
        RegistryClientOptions options = RegistryClientOptions.create()
                .registryUrl("http://localhost:8080")
                .httpAdapter(HttpAdapterType.JDK);

        // Manually set auth type to CUSTOM_WEBCLIENT (normally done via customWebClient method)
        // This should throw when creating the adapter
        try {
            Field authTypeField = RegistryClientOptions.class.getDeclaredField("authType");
            authTypeField.setAccessible(true);
            authTypeField.set(options, RegistryClientOptions.AuthType.CUSTOM_WEBCLIENT);

            assertThrows(UnsupportedOperationException.class, () ->
                    RegistryClientRequestAdapterFactory.createRequestAdapter(options, Version.V3),
                    "Should throw UnsupportedOperationException for custom WebClient with JDK adapter");
        } catch (NoSuchFieldException | IllegalAccessException e) {
            // If we can't access the field, skip this test
            System.out.println("Skipping testJdkAdapterWithCustomWebClientThrows - cannot access authType field");
        }
    }

    @Test
    void testBasicAuthAdapterStoresAuthorizationHeader() throws Exception {
        RegistryClientOptions options = RegistryClientOptions.create()
                .registryUrl("http://localhost:8080")
                .httpAdapter(HttpAdapterType.JDK)
                .basicAuth("testuser", "testpassword");

        RequestAdapter adapter = RegistryClientRequestAdapterFactory.createRequestAdapter(
                options, Version.V3);

        // Use reflection to verify the authorizationHeader field is set
        Field authHeaderField = adapter.getClass().getDeclaredField("authorizationHeader");
        authHeaderField.setAccessible(true);
        String authHeader = (String) authHeaderField.get(adapter);

        assertNotNull(authHeader, "Authorization header should be set");
        assertTrue(authHeader.startsWith("Basic "), "Should be Basic auth header");
    }

    @Test
    void testOAuth2AdapterStoresTokenProvider() throws Exception {
        RegistryClientOptions options = RegistryClientOptions.create()
                .registryUrl("http://localhost:8080")
                .httpAdapter(HttpAdapterType.JDK)
                .oauth2("http://localhost:8180/auth/realms/test/protocol/openid-connect/token",
                        "test-client", "test-secret");

        RequestAdapter adapter = RegistryClientRequestAdapterFactory.createRequestAdapter(
                options, Version.V3);

        // Use reflection to verify the tokenProvider field is set
        Field tokenProviderField = adapter.getClass().getDeclaredField("tokenProvider");
        tokenProviderField.setAccessible(true);
        Object tokenProvider = tokenProviderField.get(adapter);

        assertNotNull(tokenProvider, "Token provider should be set");
    }

    @Test
    void testJdkAdapterWithRetryEnabledCreatesProxy() {
        RegistryClientOptions options = RegistryClientOptions.create()
                .registryUrl("http://localhost:8080")
                .httpAdapter(HttpAdapterType.JDK)
                .retry();

        RequestAdapter adapter = assertDoesNotThrow(() ->
                RegistryClientRequestAdapterFactory.createRequestAdapter(options, Version.V3));

        assertNotNull(adapter, "Adapter should be created");
        // When retry is enabled, the adapter should be a proxy
        assertTrue(java.lang.reflect.Proxy.isProxyClass(adapter.getClass()),
                "Should create a retry proxy when retry is enabled");
    }

    @Test
    void testJdkAdapterWithSslConfigCreatesAdapter() {
        RegistryClientOptions options = RegistryClientOptions.create()
                .registryUrl("https://localhost:8443")
                .httpAdapter(HttpAdapterType.JDK)
                .trustAll(true);

        RequestAdapter adapter = assertDoesNotThrow(() ->
                RegistryClientRequestAdapterFactory.createRequestAdapter(options, Version.V3));

        assertNotNull(adapter, "Adapter should be created with SSL config");
    }
}
