package io.apicurio.registry.cli;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.apicurio.registry.cli.config.Config;
import io.apicurio.registry.cli.services.Client;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
public class GitOpsCommandTest {

    private static final String STATUS_PATH = "/apis/registry/v3/admin/gitops/status";
    private static final String SYNC_PATH = "/apis/registry/v3/admin/gitops/sync";
    private static final String VALIDATE_PATH = "/apis/registry/v3/admin/gitops/validate";

    private static final String CONFIG_JSON = """
            {
              "installation-version": 1,
              "config": {
                "update.check-enabled": "false"
              },
              "context": {}
            }""";

    private static final String STATUS_BODY = """
            {
              "syncState": "IDLE",
              "lastSuccessfulSync": "2026-08-31T10:15:30Z",
              "lastSyncAttempt": "2026-08-31T10:15:30Z",
              "groupCount": 5,
              "artifactCount": 12,
              "versionCount": 28,
              "errors": [],
              "sources": {
                "main-repo": "abc1234",
                "staging-repo": "def5678"
              }
            }""";

    private static final String STATUS_LOADING_BODY = """
            {
              "syncState": "LOADING",
              "groupCount": 0,
              "artifactCount": 0,
              "versionCount": 0,
              "errors": [],
              "sources": {}
            }""";

    private static final String STATUS_ERROR_BODY = """
            {
              "syncState": "ERROR",
              "lastSyncAttempt": "2026-08-31T10:15:30Z",
              "groupCount": 0,
              "artifactCount": 0,
              "versionCount": 0,
              "errors": [
                {
                  "detail": "Failed to clone repository",
                  "source": "default"
                }
              ],
              "sources": {}
            }""";

    private static final String VALIDATE_TASK_PENDING = """
            {
              "taskId": "task-001",
              "type": "pull",
              "repoId": "my-repo",
              "ref": "main",
              "state": "pending"
            }""";

    private static final String VALIDATE_TASK_SUCCESS = """
            {
              "taskId": "task-001",
              "type": "pull",
              "repoId": "my-repo",
              "ref": "main",
              "state": "completed",
              "result": "success",
              "groupCount": 3,
              "artifactCount": 7,
              "versionCount": 15,
              "errors": []
            }""";

    private static final String VALIDATE_TASK_FAILURE = """
            {
              "taskId": "task-002",
              "type": "pull",
              "repoId": "my-repo",
              "ref": "feature/broken",
              "state": "completed",
              "result": "failure",
              "groupCount": 0,
              "artifactCount": 0,
              "versionCount": 0,
              "errors": [
                {
                  "detail": "Schema contains breaking change",
                  "source": "my-repo",
                  "context": "schemas/user.avsc"
                }
              ]
            }""";

    private static WireMockServer wireMock;

    @TempDir
    Path acrHome;

    @Inject
    Config config;

    @Inject
    Client client;

    @Inject
    CommandLine.IFactory factory;

    private CommandLine cmd;
    private StringWriter out;
    private StringWriter err;

    @BeforeAll
    public static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    public static void stopWireMock() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @BeforeEach
    public void setUp() throws Exception {
        wireMock.resetAll();

        Files.writeString(acrHome.resolve("config.json"), CONFIG_JSON);
        config.reset();
        config.setAcrCurrentHomePath(acrHome);
        client.reset();

        cmd = new CommandLine(new Acr(), factory);
        out = new StringWriter();
        err = new StringWriter();
        cmd.setOut(new PrintWriter(out));
        cmd.setErr(new PrintWriter(err));
        config.setStdOut(value -> out.write(value));
        config.setStdErr(value -> err.write(value));

        assertThat(cmd.execute("context", "create", "test", "http://localhost:" + wireMock.port())).isZero();
    }

    @AfterEach
    public void tearDown() {
        config.reset();
        client.reset();
    }

    @Test
    void gitOpsHelpExitsSuccessfully() {
        assertThat(cmd.execute("gitops", "--help")).isZero();
        assertThat(out.toString()).contains("gitops");
    }

    @Test
    void gitOpsStatusHelpExitsSuccessfully() {
        assertThat(cmd.execute("gitops", "status", "--help")).isZero();
        assertThat(out.toString()).contains("status");
    }

    @Test
    void gitOpsSyncHelpExitsSuccessfully() {
        assertThat(cmd.execute("gitops", "sync", "--help")).isZero();
        assertThat(out.toString()).contains("sync");
    }

    @Test
    void gitOpsValidateHelpExitsSuccessfully() {
        assertThat(cmd.execute("gitops", "validate", "--help")).isZero();
        assertThat(out.toString()).contains("validate");
    }

    @Test
    void statusDisplaysTableOutput() {
        wireMock.stubFor(get(urlEqualTo(STATUS_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(STATUS_BODY)));

        assertThat(cmd.execute("gitops", "status")).isZero();

        final var output = out.toString();
        assertThat(output)
                .contains("Sync State")
                .contains("IDLE")
                .contains("Groups")
                .contains("5")
                .contains("Artifacts")
                .contains("12");
    }

    @Test
    void statusDisplaysJsonOutput() {
        wireMock.stubFor(get(urlEqualTo(STATUS_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(STATUS_BODY)));

        assertThat(cmd.execute("gitops", "status", "-o", "json")).isZero();

        final var output = out.toString();
        assertThat(output)
                .contains("\"syncState\"")
                .contains("IDLE");
    }

    @Test
    void syncFireAndForget() {
        wireMock.stubFor(post(urlEqualTo(SYNC_PATH))
                .willReturn(aResponse().withStatus(204)));

        assertThat(cmd.execute("gitops", "sync")).isZero();

        final var output = out.toString();
        assertThat(output).contains("Synchronization requested.");
        wireMock.verify(1, postRequestedFor(urlEqualTo(SYNC_PATH)));
    }

    @Test
    void syncWaitSucceedsWhenIdle() {
        wireMock.stubFor(post(urlEqualTo(SYNC_PATH))
                .willReturn(aResponse().withStatus(204)));

        wireMock.stubFor(get(urlEqualTo(STATUS_PATH))
                .inScenario("sync-wait")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(STATUS_LOADING_BODY))
                .willSetStateTo("loaded"));

        wireMock.stubFor(get(urlEqualTo(STATUS_PATH))
                .inScenario("sync-wait")
                .whenScenarioStateIs("loaded")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(STATUS_BODY)));

        assertThat(cmd.execute("gitops", "sync", "--wait", "--timeout", "10")).isZero();

        final var output = out.toString();
        assertThat(output)
                .contains("Sync State")
                .contains("IDLE");
    }

    @Test
    void syncWaitFailsOnError() {
        wireMock.stubFor(post(urlEqualTo(SYNC_PATH))
                .willReturn(aResponse().withStatus(204)));

        wireMock.stubFor(get(urlEqualTo(STATUS_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(STATUS_ERROR_BODY)));

        final int exitCode = cmd.execute("gitops", "sync", "--wait", "--timeout", "10");
        assertThat(exitCode).isEqualTo(1);

        final var output = out.toString();
        assertThat(output).contains("ERROR");

        final var error = err.toString();
        assertThat(error).contains("Synchronization failed.");
    }

    @Test
    void syncWaitTimesOut() {
        wireMock.stubFor(post(urlEqualTo(SYNC_PATH))
                .willReturn(aResponse().withStatus(204)));

        wireMock.stubFor(get(urlEqualTo(STATUS_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(STATUS_LOADING_BODY)));

        final int exitCode = cmd.execute("gitops", "sync", "--wait", "--timeout", "3");
        assertThat(exitCode).isEqualTo(1);

        final var error = err.toString();
        assertThat(error).contains("timed out");
    }

    @Test
    void validateTimesOut() {
        wireMock.stubFor(post(urlEqualTo(VALIDATE_PATH))
                .willReturn(aResponse()
                        .withStatus(202)
                        .withHeader("Content-Type", "application/json")
                        .withBody(VALIDATE_TASK_PENDING)));

        wireMock.stubFor(get(urlMatching(VALIDATE_PATH + "/task-001"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(VALIDATE_TASK_PENDING)));

        final int exitCode = cmd.execute("gitops", "validate", "--repo", "my-repo", "--ref", "main", "--timeout", "3");
        assertThat(exitCode).isEqualTo(1);

        final var error = err.toString();
        assertThat(error).contains("timed out");
    }

    @Test
    void validateSuccessReturnsZero() {
        wireMock.stubFor(post(urlEqualTo(VALIDATE_PATH))
                .willReturn(aResponse()
                        .withStatus(202)
                        .withHeader("Content-Type", "application/json")
                        .withBody(VALIDATE_TASK_PENDING)));

        wireMock.stubFor(get(urlMatching(VALIDATE_PATH + "/task-001"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(VALIDATE_TASK_SUCCESS)));

        wireMock.stubFor(delete(urlMatching(VALIDATE_PATH + "/task-001"))
                .willReturn(aResponse().withStatus(204)));

        assertThat(cmd.execute("gitops", "validate", "--repo", "my-repo", "--ref", "main")).isZero();

        final var output = out.toString();
        assertThat(output).contains("success");
    }

    @Test
    void validateFailureReturnsOne() {
        wireMock.stubFor(post(urlEqualTo(VALIDATE_PATH))
                .willReturn(aResponse()
                        .withStatus(202)
                        .withHeader("Content-Type", "application/json")
                        .withBody(VALIDATE_TASK_FAILURE.replace("task-002", "task-003"))));

        wireMock.stubFor(get(urlMatching(VALIDATE_PATH + "/task-003"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(VALIDATE_TASK_FAILURE.replace("task-002", "task-003"))));

        wireMock.stubFor(delete(urlMatching(VALIDATE_PATH + "/task-003"))
                .willReturn(aResponse().withStatus(204)));

        final int exitCode = cmd.execute("gitops", "validate", "--repo", "my-repo", "--ref", "feature/broken");
        assertThat(exitCode).isEqualTo(1);

        final var output = out.toString();
        assertThat(output)
                .contains("failure")
                .contains("Schema contains breaking change");
    }

    @Test
    void validateNoWaitReturnsImmediately() {
        wireMock.stubFor(post(urlEqualTo(VALIDATE_PATH))
                .willReturn(aResponse()
                        .withStatus(202)
                        .withHeader("Content-Type", "application/json")
                        .withBody(VALIDATE_TASK_PENDING)));

        assertThat(cmd.execute("gitops", "validate", "--repo", "my-repo", "--ref", "main", "--no-wait")).isZero();

        final var output = out.toString();
        assertThat(output).contains("task-001");
    }

    @Test
    void validateMissingRequiredArgsFailsWithUsageError() {
        final int exitCode = cmd.execute("gitops", "validate");
        assertThat(exitCode).isEqualTo(2);
    }

    @Test
    void validateJsonOutput() {
        wireMock.stubFor(post(urlEqualTo(VALIDATE_PATH))
                .willReturn(aResponse()
                        .withStatus(202)
                        .withHeader("Content-Type", "application/json")
                        .withBody(VALIDATE_TASK_PENDING)));

        wireMock.stubFor(get(urlMatching(VALIDATE_PATH + "/task-001"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(VALIDATE_TASK_SUCCESS)));

        wireMock.stubFor(delete(urlMatching(VALIDATE_PATH + "/task-001"))
                .willReturn(aResponse().withStatus(204)));

        assertThat(cmd.execute("gitops", "validate", "--repo", "my-repo", "--ref", "main", "-o", "json")).isZero();

        final var output = out.toString();
        assertThat(output)
                .contains("\"taskId\"")
                .contains("\"result\"")
                .contains("success");
    }
}
