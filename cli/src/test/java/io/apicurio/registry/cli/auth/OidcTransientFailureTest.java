package io.apicurio.registry.cli.auth;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.apicurio.registry.cli.common.CliException;
import io.vertx.core.Vertx;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Timeout(20)
class OidcTransientFailureTest {

    private static WireMockServer wireMock;
    private static Vertx vertx;
    private OidcDiscovery discovery;

    @BeforeAll
    static void startResources() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        vertx = Vertx.vertx();
    }

    @AfterAll
    static void stopResources() throws Exception {
        try {
            if (vertx != null) {
                vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
            }
        } finally {
            if (wireMock != null) {
                wireMock.stop();
            }
        }
    }

    @BeforeEach
    void setUp() {
        wireMock.resetAll();
        discovery = new OidcDiscovery();
        discovery.vertx = vertx;
    }

    @Test
    void serviceUnavailableReturnsTransientCode() {
        wireMock.stubFor(get(urlEqualTo("/.well-known/openid-configuration"))
                .willReturn(aResponse().withStatus(503)));

        var failure = assertThrows(CliException.class,
                () -> discovery.discoverTokenEndpoint(wireMock.baseUrl()));

        assertEquals(4, failure.getCode());
        wireMock.verify(1, getRequestedFor(urlEqualTo("/.well-known/openid-configuration")));
    }

    @Test
    void notFoundKeepsApplicationCode() {
        wireMock.stubFor(get(urlEqualTo("/.well-known/openid-configuration"))
                .willReturn(aResponse().withStatus(404)));

        var failure = assertThrows(CliException.class,
                () -> discovery.discoverTokenEndpoint(wireMock.baseUrl()));

        assertEquals(1, failure.getCode());
        wireMock.verify(1, getRequestedFor(urlEqualTo("/.well-known/openid-configuration")));
    }

    @Test
    void successfulDiscoveryReturnsTokenEndpoint() {
        wireMock.stubFor(get(urlEqualTo("/.well-known/openid-configuration"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"token_endpoint\":\""
                                + wireMock.baseUrl() + "/token\"}")));

        assertEquals(wireMock.baseUrl() + "/token",
                discovery.discoverTokenEndpoint(wireMock.baseUrl()));
        wireMock.verify(1, getRequestedFor(urlEqualTo("/.well-known/openid-configuration")));
    }
}
