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
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.apicurio.registry.cli.common.CliException.SERVER_ERROR_RETURN_CODE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that an HTTP error without a {@code ProblemDetails} body (a plain Kiota {@code ApiException})
 * is reported as a server error that names the HTTP status, not as an unexpected error.
 */
@QuarkusTest
public class ApiExceptionExitCodeTest {

    private static final String GROUP_PATH = "/apis/registry/v3/groups/test-group";

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
        wireMock.stubFor(get(urlEqualTo(GROUP_PATH)).willReturn(aResponse().withStatus(401)));

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
    public void unauthorizedWithoutBodyExitsWithServerErrorAndNamesStatus() {
        int exitCode = cmd.execute("group", "get", "test-group");

        wireMock.verify(1, getRequestedFor(urlEqualTo(GROUP_PATH)));
        assertThat(exitCode).isEqualTo(SERVER_ERROR_RETURN_CODE).isEqualTo(3);
        assertThat(err.toString())
                .contains("Error: Registry returned HTTP status 401")
                .doesNotContain("Unexpected error");
    }
}
