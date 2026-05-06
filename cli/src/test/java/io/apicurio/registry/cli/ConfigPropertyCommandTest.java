package io.apicurio.registry.cli;

import io.apicurio.registry.cli.config.Config;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
public class ConfigPropertyCommandTest {

    @Inject
    Config config;

    @Inject
    CommandLine.IFactory factory;

    private CommandLine cmd;
    private StringWriter out;
    private StringWriter err;

    @BeforeEach
    public void setUp() {
        var acrHome = Path.of(
                        getClass().getClassLoader()
                                .getResource("acr-home")
                                .getPath())
                .normalize();
        config.setAcrCurrentHomePath(acrHome);
        config.reset();
        config.setAcrCurrentHomePath(acrHome);

        cmd = new CommandLine(new Acr(), factory);
        out = new StringWriter();
        err = new StringWriter();
        cmd.setOut(new PrintWriter(out));
        cmd.setErr(new PrintWriter(err));
        config.setStdOut(value -> out.write(value));
        config.setStdErr(value -> err.write(value));
    }

    @AfterEach
    public void tearDown() {
        config.reset();
    }

    @Test
    public void testConfigHelp() {
        int exitCode = cmd.execute("config", "--help");
        assertThat(exitCode).isEqualTo(0);
        assertThat(out.toString()).contains("Usage: acr config");
    }

    @Test
    public void testConfigList() {
        int exitCode = cmd.execute("config");
        assertThat(exitCode).isEqualTo(0);
        assertThat(out.toString())
                .contains("update.check-enabled")
                .doesNotContain("internal.");
    }

    @Test
    public void testConfigListHidesInternalProperties() {
        // Set an internal property
        var configModel = config.read();
        configModel.getConfig().put("internal.update.last-check", "2026-01-01T00:00:00Z");
        config.write(configModel);

        int exitCode = cmd.execute("config");
        assertThat(exitCode).isEqualTo(0);
        assertThat(out.toString()).doesNotContain("internal.");
    }

    @Test
    public void testConfigGet() {
        int exitCode = cmd.execute("config", "get", "update.check-enabled");
        assertThat(exitCode).isEqualTo(0);
        assertThat(out.toString().trim()).isEqualTo("false");
    }

    @Test
    public void testConfigGetNonExistent() {
        int exitCode = cmd.execute("config", "get", "nonexistent.key");
        assertThat(exitCode)
                .as("Getting a non-existent property should fail")
                .isNotEqualTo(0);
    }

    @Test
    public void testConfigSet() {
        int exitCode = cmd.execute("config", "set", "test.property=test-value");
        assertThat(exitCode).isEqualTo(0);
        assertThat(out.toString()).contains("Property set");

        // Verify it was persisted
        var configModel = config.read();
        assertThat(configModel.getConfig().get("test.property")).isEqualTo("test-value");
    }

    @Test
    public void testConfigSetMultiple() {
        int exitCode = cmd.execute("config", "set", "key1=value1", "key2=value2");
        assertThat(exitCode).isEqualTo(0);
        assertThat(out.toString()).contains("2 properties set");

        var configModel = config.read();
        assertThat(configModel.getConfig().get("key1")).isEqualTo("value1");
        assertThat(configModel.getConfig().get("key2")).isEqualTo("value2");
    }

    @Test
    public void testConfigSetInvalidFormat() {
        int exitCode = cmd.execute("config", "set", "no-equals-sign");
        assertThat(exitCode)
                .as("Setting a property without '=' should fail")
                .isNotEqualTo(0);
    }

    @Test
    public void testConfigDelete() {
        // First set a property
        cmd.execute("config", "set", "to-delete=value");
        out.getBuffer().setLength(0);
        err.getBuffer().setLength(0);

        int exitCode = cmd.execute("config", "delete", "to-delete");
        assertThat(exitCode).isEqualTo(0);
        assertThat(out.toString()).contains("Property deleted");

        var configModel = config.read();
        assertThat(configModel.getConfig().containsKey("to-delete")).isFalse();
    }

    @Test
    public void testConfigDeleteNonExistent() {
        int exitCode = cmd.execute("config", "delete", "nonexistent.key");
        assertThat(exitCode)
                .as("Deleting a non-existent property should fail")
                .isNotEqualTo(0);
    }

    @Test
    public void testConfigSetAndGet() {
        cmd.execute("config", "set", "my.setting=hello");
        out.getBuffer().setLength(0);

        int exitCode = cmd.execute("config", "get", "my.setting");
        assertThat(exitCode).isEqualTo(0);
        assertThat(out.toString().trim()).isEqualTo("hello");
    }
}
