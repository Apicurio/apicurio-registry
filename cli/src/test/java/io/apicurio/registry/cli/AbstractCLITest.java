package io.apicurio.registry.cli;

import io.apicurio.registry.cli.config.Config;
import io.apicurio.registry.cli.services.Client;
import io.apicurio.registry.cli.tags.DockerRequired;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static java.time.Duration.ofSeconds;
import static java.util.Arrays.stream;
import static java.util.Optional.ofNullable;
import static java.util.stream.Stream.concat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base class for CLI tests with common setup and utility methods.
 * Subclasses must be annotated with @QuarkusTest.
 */
@DockerRequired
public abstract class AbstractCLITest {

    protected static GenericContainer<?> registryContainer;
    protected static String registryUrl;

    protected CommandLine cmd;
    protected StringWriter out;
    protected StringWriter err;

    static CommandLine createCLI() {
        var acr = new Acr();
        return new CommandLine(acr);
    }

    @BeforeAll
    public static void beforeAll() {
        var acrHome = Path.of(
                        AbstractCLITest.class.getClassLoader()
                                .getResource("acr-home")
                                .getPath())
                .normalize();
        if (!Files.exists(acrHome)) {
            throw new RuntimeException("Test resource 'acr-home' does not exist");
        }
        Config.getInstance().setAcrCurrentHomePath(acrHome);

        // Start Apicurio Registry container
        var appImage = ofNullable(System.getProperty("test.app.image"))
                .orElse("quay.io/apicurio/apicurio-registry:latest-release");
        registryContainer = new GenericContainer<>(appImage)
                .withEnv("APICURIO_REST_DELETION_GROUP_ENABLED", "true")
                .withEnv("APICURIO_REST_DELETION_ARTIFACT_ENABLED", "true")
                .withExposedPorts(8080)
                .waitingFor(Wait.forHttp("/apis/registry/v3/system/info")
                        .forStatusCode(200)
                        .withStartupTimeout(ofSeconds(60)));

        registryContainer.start();

        // Get the dynamically mapped port and construct the URL
        Integer mappedPort = registryContainer.getMappedPort(8080);
        registryUrl = "http://localhost:" + mappedPort;
    }

    @BeforeEach
    public void beforeEach() {
        Client.reset();
        cmd = createCLI();
        out = new StringWriter();
        cmd.setOut(new PrintWriter(out));
        Config.getInstance().setStdOut(value -> out.write(value));
        err = new StringWriter();
        cmd.setErr(new PrintWriter(err));
        Config.getInstance().setStdErr(value -> err.write(value));
        executeAndAssertSuccess("context", "create", "test", registryUrl);
    }

    @AfterEach
    public void afterEach() {
        executeAndAssertSuccess("context", "delete", "--all");
    }

    @AfterAll
    public static void afterAll() {
        if (registryContainer != null) {
            registryContainer.stop();
        }
    }

    protected void testHelpCommand(String... command) {
        // When
        out.getBuffer().setLength(0);
        var args = concat(stream(command), Stream.of("--help"))
                .toArray(String[]::new);
        int exitCode = cmd.execute(args);

        // Then
        assertThat(exitCode)
                .as(withCliOutput("Help command should exit with code 0."))
                .isEqualTo(0);
        String output = out.toString();
        var usage = "Usage: acr " + String.join(" ", command);
        assertThat(output)
                .as(withCliOutput("Output should contain '" + usage + "' substring."))
                .contains(usage);
    }

    protected void executeAndAssertSuccess(String... command) {
        int exitCode = cmd.execute(command);
        assertThat(exitCode)
                .as(withCliOutput("Command '" + String.join(" ", command) + "' should exit with code 0"))
                .isEqualTo(0);
    }

    protected void executeAndAssertFailure(String... command) {
        int exitCode = cmd.execute(command);
        assertThat(exitCode)
                .as(withCliOutput("Command '" + String.join(" ", command) + "' should NOT exit with code 0"))
                .isNotEqualTo(0);
    }

    protected String withCliOutput(String message) {
        return message + ":\nSTDERR:\n" + err.toString() + "\nSTDOUT:\n" + out.toString();
    }
}
