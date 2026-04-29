package io.apicurio.registry.cli;

import io.apicurio.registry.cli.config.Config;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.apicurio.registry.cli.InstallCommand.ACR_BINARY;
import static io.apicurio.registry.cli.InstallCommand.ACR_ENV;
import static io.apicurio.registry.cli.InstallCommand.ACR_HOME_PLACEHOLDER;
import static io.apicurio.registry.cli.InstallCommand.ACR_SCRIPT;
import static io.apicurio.registry.cli.InstallCommand.BIN_DIR;
import static io.apicurio.registry.cli.InstallCommand.CLI_MARKER_COMMENT;
import static io.apicurio.registry.cli.InstallCommand.COMPLETIONS;
import static io.apicurio.registry.cli.InstallCommand.CONFIG_JSON;
import static io.apicurio.registry.cli.InstallCommand.ENV_ACR_HOME;
import static io.apicurio.registry.cli.InstallCommand.ENV_ACR_INSTALL_PATH;
import static io.apicurio.registry.cli.InstallCommand.ENV_HOME;
import static io.apicurio.registry.cli.InstallCommand.README;
import static io.apicurio.registry.cli.InstallCommand.getShellConfigFile;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the InstallCommand.
 *
 * <p>These tests verify the installation process including file copying,
 * symlink creation, and shell configuration updates on both macOS (zsh)
 * and Linux (bash) environments.
 *
 * <p>Tests are split into OS-specific methods using JUnit 5 conditional
 * execution annotations to ensure proper testing on each platform.
 */
@QuarkusTest
public class InstallCommandTest {

    @Inject
    Config config;

    @Inject
    CommandLine.IFactory factory;

    private Path tempDir;
    private Path acrHome;
    private Path userHome;
    private Path installPath;

    @BeforeEach
    public void setUp(@TempDir Path tempDir) throws IOException {
        this.tempDir = tempDir;
        // Create directory structure for test
        acrHome = tempDir.resolve("acr-home");
        userHome = tempDir.resolve("user-home");
        installPath = tempDir.resolve("install-path");

        Files.createDirectories(acrHome);
        Files.createDirectories(userHome);

        // Create required files in acr-home (simulating distribution ZIP contents)
        Files.writeString(acrHome.resolve(ACR_SCRIPT), "#!/bin/bash\necho 'acr'");
        Files.writeString(acrHome.resolve(ACR_BINARY), "fake binary content");
        Files.writeString(acrHome.resolve(COMPLETIONS), "# completions");
        Files.writeString(acrHome.resolve(ACR_ENV), "export " + ENV_ACR_HOME + "=\"" + ACR_HOME_PLACEHOLDER + "\"");
        Files.writeString(acrHome.resolve(README), "# README");
        Files.writeString(acrHome.resolve(CONFIG_JSON), "{}");

        // Set up config to point to our test acr-home
        config.setAcrCurrentHomePath(acrHome);

        // Set up environment variable overrides for testing
        config.setEnvOverride(ENV_HOME, userHome.toString());
        config.setEnvOverride(ENV_ACR_INSTALL_PATH, installPath.toString());
        config.setEnvOverride(ENV_ACR_HOME, null); // Simulate fresh install
    }

    @AfterEach
    public void tearDown() {
        // Clear environment overrides to avoid test interference
        config.clearEnvOverrides();
    }

    // ========== Test Methods ==========

    @Test
    @EnabledOnOs(OS.MAC)
    public void testInstallCreatesAllNecessaryFilesOnMacOS() throws Exception {
        setupAndRunInstall();
        assertAllFilesExist();
        assertAcrHomePlaceholderReplaced();
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    public void testInstallCreatesAllNecessaryFilesOnLinux() throws Exception {
        setupAndRunInstall();
        assertAllFilesExist();
        assertAcrHomePlaceholderReplaced();
    }

    @Test
    @EnabledOnOs(OS.MAC)
    public void testInstallCreatesSymlinksOnMacOS() throws Exception {
        setupAndRunInstall();
        assertBinDirectoryExists();
        assertSymlinksCreated();
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    public void testInstallCreatesSymlinksOnLinux() throws Exception {
        setupAndRunInstall();
        assertBinDirectoryExists();
        assertSymlinksCreated();
    }

    @Test
    @EnabledOnOs(OS.MAC)
    public void testInstallUpdatesShellConfigurationOnMacOS() throws Exception {
        final String existingContent = "# My custom shell config\nexport PATH=/custom/path:$PATH\n";
        final Path shellConfig = createShellConfigFile(existingContent);
        runInstallCommand();
        assertShellConfigUpdated(shellConfig);
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    public void testInstallUpdatesShellConfigurationOnLinux() throws Exception {
        final String existingContent = "# My custom shell config\nexport PATH=/custom/path:$PATH\n";
        final Path shellConfig = createShellConfigFile(existingContent);
        runInstallCommand();
        assertShellConfigUpdated(shellConfig);
    }

    @Test
    @EnabledOnOs({OS.MAC, OS.LINUX})
    public void testInstallDoesNotDuplicateShellConfiguration() throws Exception {
        final Path shellConfig = createShellConfigFile("# existing config\n");

        runInstallCommand();
        runInstallCommand();

        final String shellConfigContent = Files.readString(shellConfig);
        final String sourceCommand = "source " + userHome.resolve(BIN_DIR).resolve(ACR_ENV);

        final int occurrences = shellConfigContent.split(sourceCommand, -1).length - 1;
        assertThat(occurrences)
            .as("Shell config should contain source command exactly once (idempotent installation)")
            .isEqualTo(1);
    }

    @Test
    @EnabledOnOs({OS.MAC, OS.LINUX})
    public void testInstallCreatesBinDirectoryIfMissing() throws Exception {
        createShellConfigFile("# existing config\n");

        final Path binPath = userHome.resolve(BIN_DIR);
        assertThat(binPath)
            .as("~/bin should not exist initially")
            .doesNotExist();

        runInstallCommand();
        assertBinDirectoryExists();
    }

    @Test
    @EnabledOnOs({OS.MAC, OS.LINUX})
    public void testInstallPreservesExistingConfigJson() throws Exception {
        createShellConfigFile("# existing config\n");

        Files.createDirectories(installPath);
        final String customConfig = "{\"custom\": \"settings\"}";
        Files.writeString(installPath.resolve(CONFIG_JSON), customConfig);

        runInstallCommand();

        final String configContent = Files.readString(installPath.resolve(CONFIG_JSON));
        assertThat(configContent)
            .as("Existing " + CONFIG_JSON + " should be preserved (not overwritten)")
            .isEqualTo(customConfig);
    }

    // ========== Helper Methods ==========

    private Path createShellConfigFile(final String content) throws IOException {
        final Path shellConfig = userHome.resolve(getShellConfigFile());
        Files.writeString(shellConfig, content);
        return shellConfig;
    }

    private void runInstallCommand() throws IOException {
        var acr = new Acr();
        var cmd = new CommandLine(acr, factory);
        cmd.execute("install");
    }

    private void setupAndRunInstall() throws IOException {
        createShellConfigFile("# existing config\n");
        runInstallCommand();
    }

    private void assertAllFilesExist() {
        assertThat(installPath.resolve(ACR_SCRIPT))
            .as("ACR script should be copied to install directory")
            .exists();
        assertThat(installPath.resolve(ACR_BINARY))
            .as("ACR binary should be copied to install directory")
            .exists();
        assertThat(installPath.resolve(README))
            .as("README should be copied to install directory")
            .exists();
        assertThat(installPath.resolve(CONFIG_JSON))
            .as("Config JSON should be copied to install directory")
            .exists();
        assertThat(installPath.resolve(COMPLETIONS))
            .as("Completions file should be copied")
            .exists();
        assertThat(installPath.resolve(ACR_ENV))
            .as("Shell environment file should be copied")
            .exists();
    }

    private void assertAcrHomePlaceholderReplaced() throws IOException {
        final String envContent = Files.readString(installPath.resolve(ACR_ENV));
        assertThat(envContent)
            .as("ACR_HOME placeholder should be replaced with actual path")
            .contains("export " + ENV_ACR_HOME + "=\"" + installPath.toAbsolutePath() + "\"")
            .doesNotContain(ACR_HOME_PLACEHOLDER);
    }

    private void assertBinDirectoryExists() {
        assertThat(userHome.resolve(BIN_DIR))
            .as("~/bin directory should be created")
            .exists();
    }

    private void assertSymlinksCreated() {
        final Path binPath = userHome.resolve(BIN_DIR);
        assertThat(binPath.resolve(ACR_SCRIPT))
            .as("ACR symlink should be created in ~/bin")
            .exists();
        assertThat(binPath.resolve(ACR_ENV))
            .as("Shell env symlink should be created in ~/bin")
            .exists();
    }

    private void assertShellConfigUpdated(final Path shellConfig) throws IOException {
        final String shellConfigContent = Files.readString(shellConfig);

        assertThat(shellConfigContent)
            .as("Shell config should preserve existing content")
            .contains("# My custom shell config")
            .as("Shell config should contain source command for CLI env")
            .contains("source " + userHome.resolve(BIN_DIR).resolve(ACR_ENV))
            .as("Shell config should contain CLI marker comment")
            .contains(CLI_MARKER_COMMENT.trim());
    }
}
