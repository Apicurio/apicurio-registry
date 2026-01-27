package io.apicurio.registry.maven;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.apicurio.registry.rest.v3.beans.IfArtifactExists;
import io.apicurio.registry.types.ArtifactType;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Collections;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for TLS configuration functionality in the Maven plugin.
 * These tests use WireMock with HTTPS to verify actual TLS connectivity.
 */
public class TlsIntegrationTest {

    private static final String CERTS_DIR = "src/test/resources/certs";
    private static final String PASSWORD = "changeit";

    private WireMockServer wireMockServer;
    private RegisterRegistryMojo mojo;
    private File tempSchemaFile;

    @BeforeEach
    void setup() throws Exception {
        // Create a temporary schema file for testing
        tempSchemaFile = Files.createTempFile("test-schema", ".avsc").toFile();
        Files.writeString(tempSchemaFile.toPath(), """
            {
              "type": "record",
              "name": "TestRecord",
              "namespace": "io.apicurio.test",
              "fields": [
                {"name": "id", "type": "string"}
              ]
            }
            """);
        tempSchemaFile.deleteOnExit();

        mojo = new RegisterRegistryMojo();
    }

    @AfterEach
    void tearDown() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
        if (tempSchemaFile != null) {
            tempSchemaFile.delete();
        }
    }

    private void startHttpsServer() {
        WireMockConfiguration config = WireMockConfiguration.wireMockConfig()
                .dynamicHttpsPort()
                .httpDisabled(true)
                .keystorePath(getResourcePath("server-keystore.jks"))
                .keystorePassword(PASSWORD)
                .keyManagerPassword(PASSWORD)
                .keystoreType("JKS");

        wireMockServer = new WireMockServer(config);
        wireMockServer.start();

        // Mock the artifact registration endpoint
        wireMockServer.stubFor(post(urlPathMatching("/apis/registry/v3/groups/.*/artifacts"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                              "version": {
                                "version": "1",
                                "globalId": 12345,
                                "contentId": 67890
                              },
                              "artifact": {
                                "groupId": "test-group",
                                "artifactId": "TestRecord"
                              }
                            }
                            """)));
    }

    private String getResourcePath(String filename) {
        return new File(CERTS_DIR, filename).getAbsolutePath();
    }

    private RegisterArtifact createTestArtifact() {
        RegisterArtifact artifact = new RegisterArtifact();
        artifact.setGroupId("test-group");
        artifact.setArtifactId("TestRecord");
        artifact.setArtifactType(ArtifactType.AVRO);
        artifact.setFile(tempSchemaFile);
        artifact.setIfExists(IfArtifactExists.FIND_OR_CREATE_VERSION);
        return artifact;
    }

    // ========================================================================
    // TLS with Trust Store Tests
    // ========================================================================

    @Test
    void testTlsWithPkcs12TrustStore() throws Exception {
        startHttpsServer();

        mojo.setRegistryUrl(wireMockServer.baseUrl() + "/apis/registry/v3");
        mojo.setTrustStorePath(getResourcePath("truststore.p12"));
        mojo.setTrustStorePassword(PASSWORD);
        mojo.setArtifacts(Collections.singletonList(createTestArtifact()));

        // Should succeed - TLS connection with proper trust store
        assertDoesNotThrow(() -> mojo.execute());

        // Verify the request was made
        wireMockServer.verify(postRequestedFor(urlPathMatching("/apis/registry/v3/groups/.*/artifacts")));
    }

    @Test
    void testTlsWithJksTrustStore() throws Exception {
        startHttpsServer();

        mojo.setRegistryUrl(wireMockServer.baseUrl() + "/apis/registry/v3");
        mojo.setTrustStorePath(getResourcePath("truststore.jks"));
        mojo.setTrustStorePassword(PASSWORD);
        mojo.setArtifacts(Collections.singletonList(createTestArtifact()));

        // Should succeed - TLS connection with JKS trust store
        assertDoesNotThrow(() -> mojo.execute());

        wireMockServer.verify(postRequestedFor(urlPathMatching("/apis/registry/v3/groups/.*/artifacts")));
    }

    @Test
    void testTlsWithPemTrustStore() throws Exception {
        startHttpsServer();

        mojo.setRegistryUrl(wireMockServer.baseUrl() + "/apis/registry/v3");
        mojo.setTrustStorePath(getResourcePath("ca-cert.pem"));
        mojo.setArtifacts(Collections.singletonList(createTestArtifact()));

        // Should succeed - TLS connection with PEM certificate
        assertDoesNotThrow(() -> mojo.execute());

        wireMockServer.verify(postRequestedFor(urlPathMatching("/apis/registry/v3/groups/.*/artifacts")));
    }

    @Test
    void testTlsWithoutTrustStore_Fails() throws Exception {
        startHttpsServer();

        mojo.setRegistryUrl(wireMockServer.baseUrl() + "/apis/registry/v3");
        // No trust store configured - should fail because server uses self-signed cert
        mojo.setArtifacts(Collections.singletonList(createTestArtifact()));

        // Should fail - no trust store to validate server certificate
        assertThrows(MojoExecutionException.class, () -> mojo.execute());
    }

    @Test
    void testTlsWithTrustAll() throws Exception {
        startHttpsServer();

        mojo.setRegistryUrl(wireMockServer.baseUrl() + "/apis/registry/v3");
        mojo.setTrustAll(true);
        mojo.setArtifacts(Collections.singletonList(createTestArtifact()));

        // Should succeed - trustAll bypasses certificate validation
        assertDoesNotThrow(() -> mojo.execute());

        wireMockServer.verify(postRequestedFor(urlPathMatching("/apis/registry/v3/groups/.*/artifacts")));
    }

    // ========================================================================
    // TLS with Authentication Tests
    // ========================================================================

    @Test
    void testTlsWithBasicAuth() throws Exception {
        startHttpsServer();

        // Add basic auth stub
        wireMockServer.stubFor(post(urlPathMatching("/apis/registry/v3/groups/.*/artifacts"))
                .withBasicAuth("testuser", "testpass")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                              "version": {
                                "version": "1",
                                "globalId": 12345,
                                "contentId": 67890
                              },
                              "artifact": {
                                "groupId": "test-group",
                                "artifactId": "TestRecord"
                              }
                            }
                            """)));

        mojo.setRegistryUrl(wireMockServer.baseUrl() + "/apis/registry/v3");
        mojo.setTrustStorePath(getResourcePath("truststore.p12"));
        mojo.setTrustStorePassword(PASSWORD);
        mojo.setUsername("testuser");
        mojo.setPassword("testpass");
        mojo.setArtifacts(Collections.singletonList(createTestArtifact()));

        // Should succeed - TLS with basic auth
        assertDoesNotThrow(() -> mojo.execute());

        wireMockServer.verify(postRequestedFor(urlPathMatching("/apis/registry/v3/groups/.*/artifacts"))
                .withBasicAuth(new com.github.tomakehurst.wiremock.client.BasicCredentials("testuser", "testpass")));
    }

    // ========================================================================
    // Explicit Store Type Tests
    // ========================================================================

    @Test
    void testExplicitTrustStoreType() throws Exception {
        startHttpsServer();

        // Rename p12 file extension to test explicit type setting
        mojo.setRegistryUrl(wireMockServer.baseUrl() + "/apis/registry/v3");
        mojo.setTrustStorePath(getResourcePath("truststore.p12"));
        mojo.setTrustStorePassword(PASSWORD);
        mojo.setTrustStoreType("PKCS12"); // Explicit type
        mojo.setArtifacts(Collections.singletonList(createTestArtifact()));

        assertDoesNotThrow(() -> mojo.execute());

        wireMockServer.verify(postRequestedFor(urlPathMatching("/apis/registry/v3/groups/.*/artifacts")));
    }
}
