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
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
public class LoggingConfigTest {

    // Matches the category key in test resource acr-home-logging/config.json.
    private static final String CONFIGURED_CATEGORY = "io.apicurio.registry.cli.services";
    private static final String UNCONFIGURED_CATEGORY = "io.apicurio.registry.cli.loggingtest.unconfigured";

    @Inject
    Config config;

    @Inject
    CommandLine.IFactory factory;

    private CommandLine cmd;
    private StringWriter out;
    private StringWriter err;
    private Level originalRootLevel;

    @BeforeEach
    public void setUp() throws Exception {
        originalRootLevel = Logger.getLogger("").getLevel();
        Logger.getLogger(CONFIGURED_CATEGORY).setLevel(null);
        Logger.getLogger(UNCONFIGURED_CATEGORY).setLevel(null);

        var acrHome = Path.of(
                        getClass().getClassLoader().getResource("acr-home-logging").toURI())
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
        Logger.getLogger("").setLevel(originalRootLevel);
        Logger.getLogger(CONFIGURED_CATEGORY).setLevel(null);
        Logger.getLogger(UNCONFIGURED_CATEGORY).setLevel(null);
        config.reset();
    }

    @Test
    public void perPackageLevelFromConfigIsApplied() {
        int exitCode = cmd.execute("config", "get", "update.check-enabled");
        assertThat(exitCode).isEqualTo(0);

        // DEBUG maps to the portable JUL level FINE (see AbstractCommand.toJulLevel).
        assertThat(Logger.getLogger(CONFIGURED_CATEGORY).getLevel()).isEqualTo(Level.FINE);
    }

    @Test
    public void unconfiguredPackageIsUntouched() {
        int exitCode = cmd.execute("config", "get", "update.check-enabled");
        assertThat(exitCode).isEqualTo(0);

        assertThat(Logger.getLogger(UNCONFIGURED_CATEGORY).getLevel()).isNull();
    }

    @Test
    public void verboseRaisesRootLoggerToFine() {
        int exitCode = cmd.execute("--verbose", "config", "get", "update.check-enabled");
        assertThat(exitCode).isEqualTo(0);

        assertThat(Logger.getLogger("").getLevel()).isEqualTo(Level.FINE);
    }
}
