package io.apicurio.registry.cli;

import io.apicurio.registry.cli.common.CliException;
import io.apicurio.registry.cli.config.Config;
import io.apicurio.registry.cli.config.ConfigModel;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static io.apicurio.registry.cli.InstallCommand.CONFIG_KEY_GLOBAL_INSTALL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link Config#write} refusing to write a read-only config file, failing with a clear,
 * actionable message before any command can report success.
 *
 * <p>A global installation shares one root-owned {@code config.json} across users, so a non-root user
 * cannot write it. Without an early check the user would see a success message immediately followed by
 * a permission-denied crash. The permission-based tests are POSIX-only and are skipped when running as
 * root, which bypasses file permissions.
 */
@QuarkusTest
public class ConfigWritableTest {

    @Inject
    Config config;

    @AfterEach
    public void tearDown() {
        config.reset();
    }

    @Test
    public void testIsGlobalInstallReflectsMarker(@TempDir Path tempDir) throws Exception {
        useHomeWithConfig(tempDir, "{\"config\":{\"" + CONFIG_KEY_GLOBAL_INSTALL + "\":\"true\"}}");
        assertThat(config.isGlobalInstall())
            .as("The global marker in config.json must be reported as a global install")
            .isTrue();

        useHomeWithConfig(tempDir, "{}");
        assertThat(config.isGlobalInstall())
            .as("A config without the marker must be reported as a per-user install")
            .isFalse();
    }

    @Test
    public void testWriteSucceedsWhenConfigIsWritable(@TempDir Path tempDir) throws Exception {
        useHomeWithConfig(tempDir, "{}");
        final ConfigModel model = config.read();
        model.getConfig().put("some.key", "some-value");

        config.write(model);

        assertThat(config.read().getConfig())
            .as("A writable config must be updated normally")
            .containsEntry("some.key", "some-value");
    }

    @Test
    @EnabledOnOs({OS.MAC, OS.LINUX})
    public void testWriteToGlobalInstallFailsWithClearMessage(@TempDir Path tempDir) throws Exception {
        assumeNotRoot();
        final Path configFile = useHomeWithConfig(tempDir,
                "{\"config\":{\"" + CONFIG_KEY_GLOBAL_INSTALL + "\":\"true\"}}");
        final ConfigModel model = config.read();
        makeReadOnly(configFile);
        try {
            assertThatThrownBy(() -> config.write(model))
                .as("Writing a read-only global config must fail instead of half-succeeding")
                .isInstanceOfSatisfying(CliException.class, ex -> {
                    assertThat(ex.getCode())
                        .as("A non-writable config is a user-actionable validation error")
                        .isEqualTo(CliException.VALIDATION_ERROR_RETURN_CODE);
                    assertThat(ex.getMessage())
                        .as("The error must explain the global-install limitation")
                        .contains("installed globally")
                        .contains("not yet supported");
                });
        } finally {
            configFile.toFile().setWritable(true, false);
        }
    }

    @Test
    @EnabledOnOs({OS.MAC, OS.LINUX})
    public void testWriteToReadOnlyPerUserConfigFailsWithGenericMessage(@TempDir Path tempDir) throws Exception {
        assumeNotRoot();
        final Path configFile = useHomeWithConfig(tempDir, "{}");
        final ConfigModel model = config.read();
        makeReadOnly(configFile);
        try {
            assertThatThrownBy(() -> config.write(model))
                .as("Writing a read-only config must fail with a clear error")
                .isInstanceOfSatisfying(CliException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(CliException.VALIDATION_ERROR_RETURN_CODE);
                    assertThat(ex.getMessage())
                        .as("A non-global read-only config should not mention global installs")
                        .contains("Cannot write to the CLI config file")
                        .doesNotContain("installed globally");
                });
        } finally {
            configFile.toFile().setWritable(true, false);
        }
    }

    /**
     * Writes a config.json with the given contents into a fresh home and points the running binary's
     * home at it, returning the config file path.
     */
    private Path useHomeWithConfig(final Path tempDir, final String configJson) throws Exception {
        final Path home = Files.createDirectories(tempDir.resolve("home-" + System.nanoTime()));
        final Path configFile = home.resolve("config.json");
        Files.writeString(configFile, configJson);
        config.reset();
        config.setAcrCurrentHomePath(home);
        return configFile;
    }

    private static void makeReadOnly(final Path file) {
        assertThat(file.toFile().setWritable(false, false))
            .as("test setup: the config file must be made read-only")
            .isTrue();
    }

    private static void assumeNotRoot() {
        Assumptions.assumeFalse("root".equals(System.getProperty("user.name")),
                "File-permission checks cannot be exercised as root");
    }
}
