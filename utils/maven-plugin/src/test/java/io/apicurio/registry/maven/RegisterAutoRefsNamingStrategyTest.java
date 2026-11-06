package io.apicurio.registry.maven;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.apicurio.registry.types.ArtifactType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the autoRefsNamingStrategy option and dryRun propagation of the autoRefs path.
 */
class RegisterAutoRefsNamingStrategyTest {

    private static final String ARTIFACTS_PATH = "/apis/registry/v3/groups/test-group/artifacts";
    private static final String RESPONSE = """
            {"version": {"groupId": "test-group", "artifactId": "x", "version": "1", "globalId": 7},
             "artifact": {"groupId": "test-group", "artifactId": "x"}}
            """;

    private final File root = Paths.get("src/test/resources/autorefs-json/citizen.json").toAbsolutePath()
            .toFile();
    private final ObjectMapper mapper = new ObjectMapper();
    private WireMockServer server;
    private RegisterRegistryMojo mojo;

    @BeforeEach
    void setup() {
        server = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        server.start();
        server.stubFor(post(urlPathEqualTo(ARTIFACTS_PATH)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody(RESPONSE)));
        mojo = new RegisterRegistryMojo();
        mojo.setRegistryUrl(server.baseUrl() + "/apis/registry/v3");
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void defaultStrategyUsesNormalizedLocations() throws Exception {
        RegisterArtifact artifact = rootArtifact();
        artifact.setAutoRefsNamingStrategy(RegisterArtifact.AutoRefsNamingStrategy.DEFAULT);
        mojo.setArtifacts(List.of(artifact));

        mojo.execute();

        assertEquals(List.of("city.json", "qualification/qualification.json", "citizenIdentifier.json",
                "citizen"), postedArtifactIds());
    }

    @Test
    void legacyStrategyUsesRawReferenceStrings() throws Exception {
        RegisterArtifact artifact = rootArtifact();
        mojo.setArtifacts(List.of(artifact));

        mojo.execute();

        assertEquals(RegisterArtifact.AutoRefsNamingStrategy.LEGACY, artifact.getAutoRefsNamingStrategy());
        assertEquals(List.of("./city.json", "./qualification/qualification.json", "citizenIdentifier.json",
                "citizen"), postedArtifactIds());
    }

    @Test
    void dryRunWithoutIfExistsSendsDryRun() throws Exception {
        RegisterArtifact artifact = rootArtifact();
        mojo.setArtifacts(List.of(artifact));
        mojo.dryRun = true;

        mojo.execute();

        List<LoggedRequest> requests = server.findAll(postRequestedFor(urlPathEqualTo(ARTIFACTS_PATH)));
        assertEquals(4, requests.size());
        for (LoggedRequest request : requests) {
            assertEquals("true", request.queryParameter("dryRun").firstValue());
        }
    }

    private RegisterArtifact rootArtifact() {
        RegisterArtifact artifact = new RegisterArtifact();
        artifact.setGroupId("test-group");
        artifact.setArtifactId("citizen");
        artifact.setArtifactType(ArtifactType.JSON);
        artifact.setFile(root);
        artifact.setAutoRefs(true);
        return artifact;
    }

    private List<String> postedArtifactIds() {
        return server.findAll(postRequestedFor(urlPathEqualTo(ARTIFACTS_PATH))).stream()
                .map(r -> {
                    try {
                        return mapper.readTree(r.getBodyAsString()).get("artifactId").asText();
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                })
                .toList();
    }
}
