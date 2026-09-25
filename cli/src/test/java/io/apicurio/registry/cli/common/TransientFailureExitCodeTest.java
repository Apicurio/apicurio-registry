package io.apicurio.registry.cli.common;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.Fault;
import com.microsoft.kiota.ApiExceptionBuilder;
import io.apicurio.registry.cli.Acr;
import io.apicurio.registry.cli.config.Config;
import io.apicurio.registry.cli.services.Client;
import io.apicurio.registry.cli.services.UpdateNotifier;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.rest.client.models.RuleViolationProblemDetails;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.http.HttpClosedException;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@Timeout(20)
public class TransientFailureExitCodeTest {

    private static final String GROUP_PATH = "/apis/registry/v3/groups/test-group";
    private static final String EXPORT_PATH = "/apis/registry/v3/admin/export";
    private static final String DOWNLOAD_PATH = "/apis/registry/v3/downloads/test-export";

    private static WireMockServer wireMock;

    @TempDir
    Path acrHome;

    @Inject
    Config config;

    @Inject
    Client client;

    @Inject
    UpdateNotifier updateNotifier;

    @Inject
    CommandLine.IFactory factory;

    private CommandLine cmd;
    private StringWriter out;
    private StringWriter err;

    @BeforeAll
    static void startServer() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopServer() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        wireMock.resetAll();
        Files.writeString(acrHome.resolve("config.json"), """
                {
                  "installation-version": 1,
                  "config": {"update.check-enabled": "false"},
                  "context": {}
                }
                """);
        config.reset();
        config.setAcrCurrentHomePath(acrHome);
        client.reset();

        out = new StringWriter();
        err = new StringWriter();
        config.setStdOut(out::write);
        config.setStdErr(err::write);
        cmd = new CommandLine(new Acr(), factory);
        cmd.setOut(new PrintWriter(out));
        cmd.setErr(new PrintWriter(err));

        assertEquals(0, cmd.execute("context", "create", "test", wireMock.baseUrl()),
                err.toString());
        clearOutput();
    }

    @AfterEach
    void tearDown() {
        config.reset();
        client.reset();
    }

    @Test
    void directTransientFailuresReturnFour() {
        for (var failure : List.of(
                new TimeoutException("Task timed out"),
                new SocketTimeoutException("Read timed out"),
                new ConnectException("Connection refused"),
                new SocketException("Connection reset"),
                new UnknownHostException("registry.example"),
                new HttpClosedException("Connection was closed"))) {
            assertFailureCode(failure, 4);
        }
    }

    @Test
    void wrappedTransientFailuresReturnFour() {
        for (var failure : List.of(
                new RuntimeException(new ExecutionException(
                        new ConnectException("Connection refused"))),
                new CliException("Polling failed",
                        new CompletionException(new TimeoutException("Task timed out")), 1),
                new RuntimeException(new CliException("Retryable failure", 4)),
                new RuntimeException(new ApiExceptionBuilder()
                        .withMessage("Service unavailable")
                        .withResponseStatusCode(503)
                        .build()))) {
            assertFailureCode(failure, 4);
        }
    }

    @Test
    void deterministicFailuresKeepApplicationCode() {
        for (var failure : List.of(
                new IOException("Could not write local file"),
                new IllegalArgumentException("Invalid timeout value: 503"),
                new InterruptedException("Interrupted"),
                new CancellationException("Cancelled"),
                new CliException("Validation task failed", 1))) {
            assertFailureCode(failure, 1);
        }
    }

    @Test
    void explicitNonTransientCodesArePreserved() {
        assertFailureCode(new CliException("Invalid input", new TimeoutException(), 2), 2);
        assertFailureCode(new CliException("Server rejected request", new TimeoutException(), 3), 3);
        assertFailureCode(new RuntimeException(
                new CliException("Invalid input", new TimeoutException(), 2)), 1);
    }

    @Test
    void knownHttpStatusTakesPrecedenceOverNestedTimeout() {
        var failure = new ApiExceptionBuilder()
                .withThrowable(new TimeoutException("Nested timeout"))
                .withResponseStatusCode(404)
                .build();
        assertFailureCode(failure, 1);
    }

    @Test
    void ruleViolationsKeepServerCode() {
        var failure = new RuleViolationProblemDetails();
        failure.setDetail("Schema violates compatibility rule");

        assertFailureCode(failure, 3);
        assertTrue(err.toString().contains("Schema violates compatibility rule"));
    }

    @Test
    void quietTransientFailureRemainsQuiet() {
        assertFailureCode(new CliException("Quiet timeout", new TimeoutException(), 1, true), 4);
        assertEquals("", err.toString());
        assertEquals("", out.toString());
    }

    @Test
    void cyclicCauseChainTerminates() {
        var first = new RuntimeException("First failure");
        var second = new RuntimeException("Second failure");
        first.initCause(second);
        second.initCause(first);

        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertFailureCode(first, 1));
    }

    @Test
    void serviceUnavailableUsesActualHttpStatus() {
        assertGroupFailure(503, 404, 4);
    }

    @Test
    void notFoundKeepsServerCodeDespiteMisleadingBody() {
        assertGroupFailure(404, 503, 3);
    }

    @Test
    void serviceUnavailableWithoutBodyReturnsFour() {
        wireMock.stubFor(get(urlEqualTo(GROUP_PATH))
                .willReturn(aResponse().withStatus(503)));

        assertEquals(4, cmd.execute("group", "get", "test-group"), err.toString());
        assertTrue(err.toString().contains("503"));
        wireMock.verify(1, getRequestedFor(urlEqualTo(GROUP_PATH)));
    }

    @Test
    void closedHttpConnectionReturnsFour() {
        wireMock.stubFor(get(urlEqualTo(GROUP_PATH))
                .willReturn(aResponse().withFault(Fault.EMPTY_RESPONSE)));

        assertEquals(4, cmd.execute("group", "get", "test-group"), err.toString());
        wireMock.verify(getRequestedFor(urlEqualTo(GROUP_PATH)));
    }

    @Test
    void successfulRequestKeepsZero() {
        wireMock.stubFor(get(urlEqualTo(GROUP_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "groupId": "test-group",
                                  "description": "Test group",
                                  "createdOn": "2026-01-01T00:00:00Z",
                                  "modifiedOn": "2026-01-01T00:00:00Z",
                                  "owner": "tester",
                                  "modifiedBy": "tester",
                                  "labels": {}
                                }
                                """)));

        assertEquals(0, cmd.execute("group", "get", "test-group"), err.toString());
        assertTrue(out.toString().contains("test-group"));
        assertEquals("", err.toString());
        wireMock.verify(1, getRequestedFor(urlEqualTo(GROUP_PATH)));
    }

    @Test
    void invalidArgumentsKeepTwo() {
        assertEquals(2, cmd.execute("--not-a-real-option"));
        assertTrue(err.toString().contains("--not-a-real-option"));
    }

    @Test
    void helpDocumentsRetryableExitCode() {
        assertEquals(0, cmd.execute("--help"));

        var help = out.toString().replaceAll("\\s+", " ");
        assertTrue(help.matches(".*\\b4[:\\s]+Transient failure.*"), help);
        assertTrue(help.contains("Retrying may succeed."), help);
    }

    @Test
    void exportServiceUnavailableReturnsFour() {
        assertExportFailure(503, 4);
    }

    @Test
    void exportNotFoundKeepsApplicationCode() {
        assertExportFailure(404, 1);
    }

    private void assertGroupFailure(int httpStatus, int bodyStatus, int expectedCode) {
        wireMock.stubFor(get(urlEqualTo(GROUP_PATH))
                .willReturn(aResponse().withStatus(httpStatus)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"status": %d, "detail": "Registry test failure"}
                                """.formatted(bodyStatus))));

        assertEquals(expectedCode, cmd.execute("group", "get", "test-group"), err.toString());
        var expectedMessage = httpStatus == 503
                ? "unexpected status code and no error class is registered for this code 503"
                : "Registry test failure";
        assertTrue(err.toString().contains(expectedMessage), err.toString());
        assertEquals("", out.toString());
        wireMock.verify(1, getRequestedFor(urlEqualTo(GROUP_PATH)));
    }

    private void assertExportFailure(int status, int expectedCode) {
        wireMock.stubFor(get(urlPathEqualTo(EXPORT_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"href\":\"" + DOWNLOAD_PATH + "\"}")));
        wireMock.stubFor(get(urlEqualTo(DOWNLOAD_PATH))
                .willReturn(aResponse().withStatus(status)));
        var archive = acrHome.resolve("export.zip");

        assertEquals(expectedCode, cmd.execute("admin", "export", "--file", archive.toString()),
                err.toString());
        assertTrue(err.toString().contains("HTTP " + status));
        assertFalse(Files.exists(archive));
        wireMock.verify(1, getRequestedFor(urlPathEqualTo(EXPORT_PATH)));
        wireMock.verify(1, getRequestedFor(urlEqualTo(DOWNLOAD_PATH)));
    }

    private void assertFailureCode(Exception failure, int expectedCode) {
        clearOutput();
        var failureCommand = new FailureCommand(config, client, updateNotifier, failure);
        var exitCode = new CommandLine(failureCommand).setCommandName("failure").execute();
        assertEquals(expectedCode, exitCode,
                failure.getClass().getSimpleName() + ": " + err);
    }

    private void clearOutput() {
        out.getBuffer().setLength(0);
        err.getBuffer().setLength(0);
    }

    private static final class FailureCommand extends AbstractCommand {
        private final Exception failure;

        private FailureCommand(Config config, Client client,
                               UpdateNotifier updateNotifier, Exception failure) {
            this.config = config;
            this.client = client;
            this.updateNotifier = updateNotifier;
            this.failure = failure;
        }

        @Override
        public void run(OutputBuffer output) throws Exception {
            throw failure;
        }
    }
}
