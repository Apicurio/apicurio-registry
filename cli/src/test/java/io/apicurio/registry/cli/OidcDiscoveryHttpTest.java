package io.apicurio.registry.cli;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.apicurio.registry.cli.auth.OidcDiscovery;
import io.apicurio.registry.cli.common.CliException;
import io.vertx.core.Vertx;
import java.lang.reflect.Field;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class OidcDiscoveryHttpTest {

    private static WireMockServer wireMock;
    private static OidcDiscovery oidcDiscovery;
    private static Vertx vertx;

    @BeforeAll
    static void setup() throws Exception {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        vertx = Vertx.vertx();
        oidcDiscovery = new OidcDiscovery();
        final Field vertxField = OidcDiscovery.class.getDeclaredField("vertx");
        vertxField.setAccessible(true);
        vertxField.set(oidcDiscovery, vertx);
    }

    @AfterAll
    static void teardown() {
        if (wireMock != null) {
            wireMock.stop();
        }
        if (vertx != null) {
            vertx.close();
        }
    }

    @BeforeEach
    void resetStubs() {
        wireMock.resetAll();
    }

    private static String authServerUrl() {
        return "http://localhost:" + wireMock.port() + "/realms/test";
    }

    private static String tokenEndpointUrl() {
        return "http://localhost:" + wireMock.port() + "/realms/test/protocol/openid-connect/token";
    }

    @Test
    public void testDiscoveryHappyPath() {
        wireMock.stubFor(get(urlEqualTo("/realms/test/.well-known/openid-configuration"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"token_endpoint\": \"" + tokenEndpointUrl() + "\"}")));

        final var result = oidcDiscovery.discoverTokenEndpoint(authServerUrl());
        assertThat(result).isEqualTo(tokenEndpointUrl());
    }

    @Test
    public void testDiscoveryFollowsRedirects() {
        wireMock.stubFor(get(urlEqualTo("/realms/test/.well-known/openid-configuration"))
                .willReturn(aResponse()
                        .withStatus(302)
                        .withHeader("Location", "http://localhost:" + wireMock.port()
                                + "/realms/redirected/.well-known/openid-configuration")));

        wireMock.stubFor(get(urlEqualTo("/realms/redirected/.well-known/openid-configuration"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"token_endpoint\": \"" + tokenEndpointUrl() + "\"}")));

        final var result = oidcDiscovery.discoverTokenEndpoint(authServerUrl());
        assertThat(result).isEqualTo(tokenEndpointUrl());
    }

    @Test
    public void testDiscoveryReturns404() {
        wireMock.stubFor(get(urlEqualTo("/realms/test/.well-known/openid-configuration"))
                .willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> oidcDiscovery.discoverTokenEndpoint(authServerUrl()))
                .isInstanceOf(CliException.class)
                .hasMessageContaining("HTTP 404");
    }

    @Test
    public void testDiscoveryMissingTokenEndpoint() {
        wireMock.stubFor(get(urlEqualTo("/realms/test/.well-known/openid-configuration"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"issuer\": \"http://localhost\"}")));

        assertThatThrownBy(() -> oidcDiscovery.discoverTokenEndpoint(authServerUrl()))
                .isInstanceOf(CliException.class)
                .hasMessageContaining("token_endpoint");
    }

    @Test
    public void testDiscoveryInvalidTokenEndpointScheme() {
        wireMock.stubFor(get(urlEqualTo("/realms/test/.well-known/openid-configuration"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"token_endpoint\": \"file:///etc/passwd\"}")));

        assertThatThrownBy(() -> oidcDiscovery.discoverTokenEndpoint(authServerUrl()))
                .isInstanceOf(CliException.class)
                .hasMessageContaining("must start with http:// or https://");
    }

    @Test
    public void testDiscoveryOversizedResponseRejectedByContentLength() {
        wireMock.stubFor(get(urlEqualTo("/realms/test/.well-known/openid-configuration"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("Content-Length", "2097152")
                        .withBody("{\"token_endpoint\": \"http://localhost/token\"}")));

        assertThatThrownBy(() -> oidcDiscovery.discoverTokenEndpoint(authServerUrl()))
                .isInstanceOf(CliException.class)
                .hasMessageContaining("exceeds maximum size");
    }

    @Test
    public void testDiscoveryOversizedChunkedResponseRejected() {
        final var oversizedBody = "x".repeat(1_048_576 + 1);
        wireMock.stubFor(get(urlEqualTo("/realms/test/.well-known/openid-configuration"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(oversizedBody)));

        assertThatThrownBy(() -> oidcDiscovery.discoverTokenEndpoint(authServerUrl()))
                .isInstanceOf(CliException.class)
                .hasMessageContaining("exceeds maximum size");
    }

    @Test
    public void testDiscoveryInvalidJson() {
        wireMock.stubFor(get(urlEqualTo("/realms/test/.well-known/openid-configuration"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("not json")));

        assertThatThrownBy(() -> oidcDiscovery.discoverTokenEndpoint(authServerUrl()))
                .isInstanceOf(CliException.class)
                .hasMessageContaining("Failed to parse");
    }

    @Test
    public void testDiscoveryRejectsTokenEndpointFromDifferentOrigin() {
        wireMock.stubFor(get(urlEqualTo("/realms/test/.well-known/openid-configuration"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"token_endpoint\": \"https://attacker.example/token\"}")));

        assertThatThrownBy(() -> oidcDiscovery.discoverTokenEndpoint(authServerUrl()))
                .isInstanceOf(CliException.class)
                .hasMessageContaining("different origin");
    }

    @Test
    public void testDiscoveryFollowsRelativeRedirect() {
        wireMock.stubFor(get(urlEqualTo("/realms/test/.well-known/openid-configuration"))
                .willReturn(aResponse()
                        .withStatus(302)
                        .withHeader("Location",
                                "/realms/test/canonical/.well-known/openid-configuration")));

        wireMock.stubFor(get(urlEqualTo("/realms/test/canonical/.well-known/openid-configuration"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"token_endpoint\": \"" + tokenEndpointUrl() + "\"}")));

        final var result = oidcDiscovery.discoverTokenEndpoint(authServerUrl());
        assertThat(result).isEqualTo(tokenEndpointUrl());
    }

    @Test
    public void testDiscoveryRejectsRedirectToDifferentHost() {
        wireMock.stubFor(get(urlEqualTo("/realms/test/.well-known/openid-configuration"))
                .willReturn(aResponse()
                        .withStatus(302)
                        .withHeader("Location", "http://attacker.example/steal")));

        assertThatThrownBy(() -> oidcDiscovery.discoverTokenEndpoint(authServerUrl()))
                .isInstanceOf(CliException.class)
                .hasMessageContaining("different origin");
    }
}
