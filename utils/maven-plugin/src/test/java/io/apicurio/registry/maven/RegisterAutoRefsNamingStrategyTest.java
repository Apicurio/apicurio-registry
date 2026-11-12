package io.apicurio.registry.maven;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.apicurio.registry.rest.v3.beans.IfArtifactExists;
import io.apicurio.registry.types.ArtifactType;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the autoRefsNamingStrategy option, request options, error reporting and interrupt handling of the
 * autoRefs path.
 */
class RegisterAutoRefsNamingStrategyTest {

    private static final String ARTIFACTS_PATH = "/apis/registry/v3/groups/test-group/artifacts";
    private static final String RESPONSE = """
            {"version": {"groupId": "test-group", "artifactId": "x", "version": "1", "globalId": 7},
             "artifact": {"groupId": "test-group", "artifactId": "x"}}
            """;

    private final File root = Paths.get("src/test/resources/autorefs-json/citizen.json").toAbsolutePath()
            .toFile();
    private final ObjectMapper mapper = new JsonMapper();
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
    void dryRunSendsOnlyLeavesAndWarnsAboutSkippedArtifacts() throws Exception {
        RegisterArtifact artifact = rootArtifact();
        mojo.setArtifacts(List.of(artifact));
        mojo.dryRun = true;
        List<String> warnings = new ArrayList<>();
        mojo.setLog(new SystemStreamLog() {
            @Override
            public void warn(CharSequence content) {
                warnings.add(content.toString());
            }
        });

        mojo.execute();

        List<LoggedRequest> requests = server.findAll(postRequestedFor(urlPathEqualTo(ARTIFACTS_PATH)));
        assertEquals(3, requests.size());
        for (LoggedRequest request : requests) {
            assertEquals("true", request.queryParameter("dryRun").firstValue());
        }
        assertEquals(List.of("./city.json", "./qualification/qualification.json", "citizenIdentifier.json"),
                postedArtifactIds());
        assertEquals(List.of("[test-group] / [citizen]: Skipped in dry run: depends on artifacts that are not "
                + "registered yet: test-group/./city.json, test-group/./qualification/qualification.json, "
                + "test-group/citizenIdentifier.json"),
                warnings.stream().filter(w -> w.contains("Skipped in dry run")).toList());
    }

    @Test
    void ifExistsReachesEveryAutoRefsRequest() throws Exception {
        RegisterArtifact artifact = rootArtifact();
        artifact.setIfExists(IfArtifactExists.FIND_OR_CREATE_VERSION);
        mojo.setArtifacts(List.of(artifact));

        mojo.execute();

        List<LoggedRequest> requests = server.findAll(postRequestedFor(urlPathEqualTo(ARTIFACTS_PATH)));
        assertEquals(4, requests.size());
        for (LoggedRequest request : requests) {
            assertEquals("FIND_OR_CREATE_VERSION", request.queryParameter("ifExists").firstValue());
        }
    }

    @Test
    void unresolvedReferencesAreReportedTogether(@TempDir Path dir) throws Exception {
        Path schema = dir.resolve("root.json");
        Files.writeString(schema, """
                {"$schema": "http://json-schema.org/draft-07/schema#", "type": "object",
                 "properties": {"a": {"$ref": "missing-a.json"}, "b": {"$ref": "missing-b.json"}}}
                """);
        RegisterArtifact artifact = rootArtifact();
        artifact.setFile(schema.toFile());
        mojo.setArtifacts(List.of(artifact));
        List<Throwable> logged = new ArrayList<>();
        mojo.setLog(new SystemStreamLog() {
            @Override
            public void error(CharSequence content, Throwable error) {
                logged.add(error);
            }
        });

        MojoExecutionException ex = assertThrows(MojoExecutionException.class, mojo::executeInternal);

        assertEquals("Errors while registering artifacts ...", ex.getMessage());
        assertEquals(1, logged.size());
        assertEquals("Unable to resolve references: missing-a.json (from root.json), "
                + "missing-b.json (from root.json)", logged.get(0).getMessage());
        assertEquals(0, server.findAll(postRequestedFor(urlPathEqualTo(ARTIFACTS_PATH))).size());
    }

    @Test
    void interruptDuringChildRegistrationKeepsChildCoordinates() throws Exception {
        server.stubFor(post(urlPathEqualTo(ARTIFACTS_PATH)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody(RESPONSE).withFixedDelay(30_000)));
        mojo.setArtifacts(List.of(rootArtifact()));
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        AtomicBoolean interruptedAfter = new AtomicBoolean();
        Thread worker = new Thread(() -> {
            try {
                mojo.executeInternal();
            } catch (Throwable t) {
                thrown.set(t);
            }
            interruptedAfter.set(Thread.currentThread().isInterrupted());
        });

        worker.start();
        await().atMost(Duration.ofSeconds(10)).until(() -> !server.getAllServeEvents().isEmpty());
        worker.interrupt();
        worker.join(15_000);

        assertFalse(worker.isAlive());
        MojoExecutionException ex = assertInstanceOf(MojoExecutionException.class, thrown.get());
        assertEquals("Interrupted while registering artifact [test-group] / [citizen]: "
                + "Interrupted while registering artifact [test-group] / [./city.json]", ex.getMessage());
        assertInstanceOf(InterruptedException.class, ex.getCause());
        assertTrue(interruptedAfter.get());
        assertEquals(1, server.getAllServeEvents().size());
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
                        return mapper.readTree(r.getBodyAsString()).get("artifactId").asString();
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                })
                .toList();
    }
}
