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
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@code --verbose} makes the CLI log the raw request and response of a Registry call.
 */
@QuarkusTest
public class VerboseHttpLoggingTest {

    private static final String HTTP_LOGGER = "io.apicurio.registry.client.http";

    private static final String GROUP_PATH = "/apis/registry/v3/groups/test-group";

    private static final String GROUP_BODY = """
            {"groupId":"test-group","description":"A test group",\
            "createdOn":"2026-01-01T00:00:00Z","owner":"tester",\
            "modifiedOn":"2026-01-01T00:00:00Z","modifiedBy":"tester","labels":{}}""";

    private static final String CONFIG_JSON = """
            {
              "installation-version": 1,
              "config": {
                "update.check-enabled": "false"
              },
              "context": {}
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

    private final List<String> logged = new CopyOnWriteArrayList<>();

    private CommandLine cmd;
    private StringWriter out;
    private StringWriter err;
    private Handler captureHandler;
    private Level originalRootLevel;
    private Level originalHttpLevel;

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
        wireMock.stubFor(get(urlEqualTo(GROUP_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(GROUP_BODY)));

        Files.writeString(acrHome.resolve("config.json"), CONFIG_JSON);
        config.reset();
        config.setAcrCurrentHomePath(acrHome);
        client.reset();

        originalRootLevel = Logger.getLogger("").getLevel();
        originalHttpLevel = Logger.getLogger(HTTP_LOGGER).getLevel();
        captureHandler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                logged.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        captureHandler.setLevel(Level.ALL);
        Logger.getLogger(HTTP_LOGGER).addHandler(captureHandler);
        // Enabled up front, so that a test without records proves the interceptor was not installed,
        // rather than that the logger happened to be too coarse.
        Logger.getLogger(HTTP_LOGGER).setLevel(Level.FINE);

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
        Logger.getLogger(HTTP_LOGGER).removeHandler(captureHandler);
        Logger.getLogger(HTTP_LOGGER).setLevel(originalHttpLevel);
        Logger.getLogger("").setLevel(originalRootLevel);
        logged.clear();
        config.reset();
        client.reset();
    }

    @Test
    public void verboseLogsRawRequestAndResponse() {
        assertThat(cmd.execute("--verbose", "group", "get", "test-group")).isZero();

        var request = recordStartingWith("HTTP request:");
        assertThat(request).contains("> GET http://localhost:" + wireMock.port() + GROUP_PATH);

        var response = recordStartingWith("HTTP response:");
        assertThat(response).contains("< 200 OK");
        assertThat(response).contains("< Content-Type: application/json");
        assertThat(response).contains("< " + GROUP_BODY);
    }

    @Test
    public void withoutVerboseNothingIsLogged() {
        assertThat(cmd.execute("group", "get", "test-group")).isZero();

        assertThat(logged).isEmpty();
    }

    private String recordStartingWith(String prefix) {
        return logged.stream()
                .filter(record -> record.startsWith(prefix))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No log record starting with '" + prefix + "' in " + logged));
    }
}
