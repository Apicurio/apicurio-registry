package io.apicurio.registry.client;

import io.apicurio.registry.rest.client.RegistryClient;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for {@link RegistryClientFactory}.
 * Tests both the new unified API and the deprecated legacy methods.
 */
public class RegistryClientFactoryTest {

    private Vertx vertx;
    private static final String TEST_REGISTRY_URL = "http://localhost:8080/apis/registry/v3";
    private static final String TEST_TOKEN_ENDPOINT = "http://localhost:8090/realms/registry/protocol/openid-connect/token";

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx();
    }

    @AfterEach
    void tearDown() {
        if (vertx != null) {
            vertx.close();
        }
    }

    @Test
    void testCreateAnonymousClient() {
        RegistryClientOptions options = RegistryClientOptions.create().registryUrl(TEST_REGISTRY_URL);
        RegistryClient client = RegistryClientFactory.create(options);
        assertNotNull(client);
    }

    @Test
    void testCreateAnonymousClientWithVertx() {
        RegistryClientOptions options = RegistryClientOptions.create().registryUrl(TEST_REGISTRY_URL).vertx(vertx);
        RegistryClient client = RegistryClientFactory.create(options);
        assertNotNull(client);
    }

    @Test
    void testCreateWithBasicAuth() {
        RegistryClientOptions options = RegistryClientOptions.create().registryUrl(TEST_REGISTRY_URL)
                .basicAuth("testuser", "testpass");
        RegistryClient client = RegistryClientFactory.create(options);
        assertNotNull(client);
    }

    @Test
    void testCreateWithBasicAuthAndVertx() {
        RegistryClientOptions options = RegistryClientOptions.create().registryUrl(TEST_REGISTRY_URL)
                .basicAuth("testuser", "testpass").vertx(vertx);
        RegistryClient client = RegistryClientFactory.create(options);
        assertNotNull(client);
    }

    @Test
    void testCreateWithOAuth2() {
        RegistryClientOptions options = RegistryClientOptions.create().registryUrl(TEST_REGISTRY_URL)
                .oauth2(TEST_TOKEN_ENDPOINT, "client-id", "client-secret");
        RegistryClient client = RegistryClientFactory.create(options);
        assertNotNull(client);
    }

    @Test
    void testCreateWithOAuth2AndScope() {
        RegistryClientOptions options = RegistryClientOptions.create().registryUrl(TEST_REGISTRY_URL)
                .oauth2(TEST_TOKEN_ENDPOINT, "client-id", "client-secret", "openid");
        RegistryClient client = RegistryClientFactory.create(options);
        assertNotNull(client);
    }

    @Test
    void testCreateWithOAuth2AndVertx() {
        RegistryClientOptions options = RegistryClientOptions.create().registryUrl(TEST_REGISTRY_URL)
                .oauth2(TEST_TOKEN_ENDPOINT, "client-id", "client-secret").vertx(vertx);
        RegistryClient client = RegistryClientFactory.create(options);
        assertNotNull(client);
    }

    // Validation tests

    @Test
    void testCreateWithNullUrl() {
        RegistryClientOptions options = RegistryClientOptions.create().registryUrl(null);
        assertThrows(IllegalArgumentException.class, () ->
            RegistryClientFactory.create(options));
    }

    @Test
    void testCreateWithEmptyUrl() {
        RegistryClientOptions options = RegistryClientOptions.create().registryUrl("");
        assertThrows(IllegalArgumentException.class, () ->
            RegistryClientFactory.create(options));
    }

    @Test
    void testCreateWithBasicAuthNullUsername() {
        RegistryClientOptions options = RegistryClientOptions.create().registryUrl(TEST_REGISTRY_URL)
                .basicAuth(null, "password");
        assertThrows(IllegalArgumentException.class, () ->
            RegistryClientFactory.create(options));
    }

    @Test
    void testCreateWithBasicAuthEmptyUsername() {
        RegistryClientOptions options = RegistryClientOptions.create().registryUrl(TEST_REGISTRY_URL)
                .basicAuth("", "password");
        assertThrows(IllegalArgumentException.class, () ->
                RegistryClientFactory.create(options));
    }

    @Test
    void testCreateWithBasicAuthNullPassword() {
        RegistryClientOptions options = RegistryClientOptions.create().registryUrl(TEST_REGISTRY_URL)
                .basicAuth("username", null);
        assertThrows(IllegalArgumentException.class, () ->
                RegistryClientFactory.create(options));
    }

    @Test
    void testCreateWithOAuth2NullTokenEndpoint() {
        RegistryClientOptions options = RegistryClientOptions.create().registryUrl(TEST_REGISTRY_URL)
                .oauth2(null, "client-id", "client-secret");
        assertThrows(IllegalArgumentException.class, () ->
                RegistryClientFactory.create(options));
    }

    @Test
    void testCreateWithOAuth2NullClientId() {
        RegistryClientOptions options = RegistryClientOptions.create().registryUrl(TEST_REGISTRY_URL)
                .oauth2(TEST_TOKEN_ENDPOINT, null, "client-secret");
        assertThrows(IllegalArgumentException.class, () ->
                RegistryClientFactory.create(options));
    }

    @Test
    void testCreateWithOAuth2NullClientSecret() {
        RegistryClientOptions options = RegistryClientOptions.create().registryUrl(TEST_REGISTRY_URL)
                .oauth2(TEST_TOKEN_ENDPOINT, "client-id", null);
        assertThrows(IllegalArgumentException.class, () ->
                RegistryClientFactory.create(options));
    }

    @Test
    void testCreateWithCustomWebClientNull() {
        RegistryClientOptions options = RegistryClientOptions.create().registryUrl(TEST_REGISTRY_URL)
                .customWebClient(null);
        assertThrows(IllegalArgumentException.class, () ->
                RegistryClientFactory.create(options));
    }

    @Test
    void testCreateWithVertxNull() {
        RegistryClientOptions options = RegistryClientOptions.create().registryUrl(TEST_REGISTRY_URL).vertx(null);
        RegistryClient client = RegistryClientFactory.create(options);
        assertNotNull(client);
    }

}
