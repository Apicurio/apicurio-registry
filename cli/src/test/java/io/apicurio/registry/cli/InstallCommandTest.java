package io.apicurio.registry.cli;

import io.apicurio.registry.cli.config.Config;
import io.apicurio.registry.cli.utils.OutputBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.apicurio.registry.cli.InstallCommand.ACR_HOME_PLACEHOLDER;
import static io.apicurio.registry.cli.InstallCommand.ACR_JAR;
import static io.apicurio.registry.cli.InstallCommand.ACR_SCRIPT;
import static io.apicurio.registry.cli.InstallCommand.BASH_ENV;
import static io.apicurio.registry.cli.InstallCommand.BIN_DIR;
import static io.apicurio.registry.cli.InstallCommand.CLI_MARKER_COMMENT;
import static io.apicurio.registry.cli.InstallCommand.COMPLETIONS;
import static io.apicurio.registry.cli.InstallCommand.CONFIG_JSON;
import static io.apicurio.registry.cli.InstallCommand.ENV_ACR_HOME;
import static io.apicurio.registry.cli.InstallCommand.ENV_ACR_INSTALL_PATH;
import static io.apicurio.registry.cli.InstallCommand.ENV_HOME;
import static io.apicurio.registry.cli.InstallCommand.README;
import static io.apicurio.registry.cli.InstallCommand.ZSH_ENV;
import static io.apicurio.registry.cli.InstallCommand.getShellConfigFile;
import static io.apicurio.registry.cli.InstallCommand.getShellEnvFile;
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
public class InstallCommandTest {

    @TempDir
    Path tempDir;

    private Path acrHome;
    private Path userHome;
    private Path installPath;

    @BeforeEach
    public void setUp() throws IOException {
        // Create directory structure for test
        acrHome = tempDir.resolve("acr-home");
        userHome = tempDir.resolve("user-home");
        installPath = tempDir.resolve("install-path");

        Files.createDirectories(acrHome);
        Files.createDirectories(userHome);

        // Create required files in acr-home (simulating distribution)
        Files.writeString(acrHome.resolve(ACR_SCRIPT), "#!/bin/bash\necho 'acr'");
        Files.writeString(acrHome.resolve(ACR_JAR), "fake jar content");
        Files.writeString(acrHome.resolve(COMPLETIONS), "# completions");
        Files.writeString(acrHome.resolve(BASH_ENV), "export " + ENV_ACR_HOME + "=\"" + ACR_HOME_PLACEHOLDER + "\"");
        Files.writeString(acrHome.resolve(ZSH_ENV), "export " + ENV_ACR_HOME + "=\"" + ACR_HOME_PLACEHOLDER + "\"");
        Files.writeString(acrHome.resolve(README), "# README");
        Files.writeString(acrHome.resolve(CONFIG_JSON), "{}");

        // Set up config to point to our test acr-home
        Config.getInstance().setAcrCurrentHomePath(acrHome);

        // Set up environment variable overrides for testing
        Config.getInstance().setEnvOverride(ENV_HOME, userHome.toString());
        Config.getInstance().setEnvOverride(ENV_ACR_INSTALL_PATH, installPath.toString());
        Config.getInstance().setEnvOverride(ENV_ACR_HOME, null); // Simulate fresh install
    }

    @AfterEach
    public void tearDown() {
        // Clear environment overrides to avoid test interference
        Config.getInstance().clearEnvOverrides();
    }

    // ========== Test Methods ==========

    @Test
    @EnabledOnOs(OS.MAC)
    public void testInstallCreatesAllNecessaryFilesOnMacOS() throws Exception {
        // Given/When: Shell config exists and install command is executed
        setupAndRunInstall();

        // Then: Verify all files were copied correctly
        assertAllFilesExist();
        assertAcrHomePlaceholderReplaced();
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    public void testInstallCreatesAllNecessaryFilesOnLinux() throws Exception {
        // Given/When: Shell config exists and install command is executed
        setupAndRunInstall();

        // Then: Verify all files were copied correctly
        assertAllFilesExist();
        assertAcrHomePlaceholderReplaced();
    }

    @Test
    @EnabledOnOs(OS.MAC)
    public void testInstallCreatesSymlinksOnMacOS() throws Exception {
        // Given/When: Shell config exists and install command is executed
        setupAndRunInstall();

        // Then: Verify symlinks were created
        assertBinDirectoryExists();
        assertSymlinksCreated();
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    public void testInstallCreatesSymlinksOnLinux() throws Exception {
        // Given/When: Shell config exists and install command is executed
        setupAndRunInstall();

        // Then: Verify symlinks were created
        assertBinDirectoryExists();
        assertSymlinksCreated();
    }

    @Test
    @EnabledOnOs(OS.MAC)
    public void testInstallUpdatesShellConfigurationOnMacOS() throws Exception {
        // Given: Shell config file with existing content
        final String existingContent = "# My custom shell config\nexport PATH=/custom/path:$PATH\n";
        final Path shellConfig = createShellConfigFile(existingContent);

        // When: Install command is executed
        runInstallCommand();

        // Then: Verify shell config was updated
        assertShellConfigUpdated(shellConfig);
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    public void testInstallUpdatesShellConfigurationOnLinux() throws Exception {
        // Given: Shell config file with existing content
        final String existingContent = "# My custom shell config\nexport PATH=/custom/path:$PATH\n";
        final Path shellConfig = createShellConfigFile(existingContent);

        // When: Install command is executed
        runInstallCommand();

        // Then: Verify shell config was updated
        assertShellConfigUpdated(shellConfig);
    }

    @Test
    @EnabledOnOs({OS.MAC, OS.LINUX})
    public void testInstallDoesNotDuplicateShellConfiguration() throws Exception {
        // Given: Shell config file exists
        final Path shellConfig = createShellConfigFile("# existing config\n");

        // When: Install command is executed twice
        runInstallCommand();
        runInstallCommand();

        // Then: Verify shell config contains source line only once
        final String shellConfigContent = Files.readString(shellConfig);
        final String shellEnvFile = getShellEnvFile();
        final String sourceCommand = "source " + userHome.resolve(BIN_DIR).resolve(shellEnvFile);

        final int occurrences = shellConfigContent.split(sourceCommand, -1).length - 1;
        assertThat(occurrences)
            .as("Shell config should contain source command exactly once (idempotent installation)")
            .isEqualTo(1);
    }

    @Test
    @EnabledOnOs({OS.MAC, OS.LINUX})
    public void testInstallCreatesBinDirectoryIfMissing() throws Exception {
        // Given: Shell config file exists and ~/bin does not exist
        createShellConfigFile("# existing config\n");

        final Path binPath = userHome.resolve(BIN_DIR);
        assertThat(binPath)
            .as("~/bin should not exist initially")
            .doesNotExist();

        // When: Install command is executed
        runInstallCommand();

        // Then: Verify bin directory was created
        assertBinDirectoryExists();
    }

    @Test
    @EnabledOnOs({OS.MAC, OS.LINUX})
    public void testInstallPreservesExistingConfigJson() throws Exception {
        // Given: Existing installation with custom config.json
        createShellConfigFile("# existing config\n");

        Files.createDirectories(installPath);
        final String customConfig = "{\"custom\": \"settings\"}";
        Files.writeString(installPath.resolve(CONFIG_JSON), customConfig);

        // When: Install command is executed
        runInstallCommand();

        // Then: Verify config.json was NOT overwritten
        final String configContent = Files.readString(installPath.resolve(CONFIG_JSON));
        assertThat(configContent)
            .as("Existing " + CONFIG_JSON + " should be preserved (not overwritten)")
            .isEqualTo(customConfig);
    }

    // ========== Helper Methods ==========

    /**
     * Creates a shell configuration file (.bashrc or .zshrc) with the specified content.
     * The appropriate file is determined based on the current OS.
     *
     * @param content the content to write to the shell config file
     * @return the path to the created shell config file
     */
    private Path createShellConfigFile(final String content) throws IOException {
        final Path shellConfig = userHome.resolve(getShellConfigFile());
        Files.writeString(shellConfig, content);
        return shellConfig;
    }

    /**
     * Executes the install command.
     */
    private void runInstallCommand() throws IOException {
        final InstallCommand command = new InstallCommand();
        final OutputBuffer output = new OutputBuffer(Config.getInstance().getStdOut(), Config.getInstance().getStdErr());
        command.run(output);
    }

    /**
     * Sets up a standard test environment and runs the install command.
     * Creates a shell config file with basic content and executes the installation.
     */
    private void setupAndRunInstall() throws IOException {
        createShellConfigFile("# existing config\n");
        runInstallCommand();
    }

    /**
     * Verifies that all necessary files were copied to the install directory.
     * This includes common files (acr script, JAR, README, config.json),
     * completions file, and shell-specific environment file.
     */
    private void assertAllFilesExist() {
        // Verify common files
        assertThat(installPath.resolve(ACR_SCRIPT))
            .as("ACR script should be copied to install directory")
            .exists();
        assertThat(installPath.resolve(ACR_JAR))
            .as("ACR JAR should be copied to install directory")
            .exists();
        assertThat(installPath.resolve(README))
            .as("README should be copied to install directory")
            .exists();
        assertThat(installPath.resolve(CONFIG_JSON))
            .as("Config JSON should be copied to install directory")
            .exists();

        // Verify completions file
        assertThat(installPath.resolve(COMPLETIONS))
            .as("Completions file should be copied")
            .exists();

        // Verify shell-specific environment file
        final String envFile = getShellEnvFile();
        assertThat(installPath.resolve(envFile))
            .as("Shell environment file should be copied")
            .exists();
    }

    /**
     * Verifies that the ACR_HOME placeholder in the shell environment file was replaced with the actual install path.
     */
    private void assertAcrHomePlaceholderReplaced() throws IOException {
        final String envFile = getShellEnvFile();
        final String envContent = Files.readString(installPath.resolve(envFile));
        assertThat(envContent)
            .as("ACR_HOME placeholder should be replaced with actual path")
            .contains("export " + ENV_ACR_HOME + "=\"" + installPath.toAbsolutePath() + "\"")
            .doesNotContain(ACR_HOME_PLACEHOLDER);
    }

    /**
     * Verifies that the ~/bin directory was created.
     */
    private void assertBinDirectoryExists() {
        final Path binPath = userHome.resolve(BIN_DIR);
        assertThat(binPath)
            .as("~/bin directory should be created")
            .exists();
    }

    /**
     * Verifies that symlinks were created in ~/bin for the acr script and shell environment file.
     */
    private void assertSymlinksCreated() {
        final Path binPath = userHome.resolve(BIN_DIR);
        assertThat(binPath.resolve(ACR_SCRIPT))
            .as("ACR symlink should be created in ~/bin")
            .exists();

        final String envFile = getShellEnvFile();
        assertThat(binPath.resolve(envFile))
            .as("Shell env symlink should be created in ~/bin")
            .exists();
    }

    /**
     * Verifies that the shell configuration file was updated correctly.
     * Checks that existing content is preserved, source command was added, and CLI marker is present.
     *
     * @param shellConfig the path to the shell config file
     */
    private void assertShellConfigUpdated(final Path shellConfig) throws IOException {
        final String shellConfigContent = Files.readString(shellConfig);
        final String envFile = getShellEnvFile();

        assertThat(shellConfigContent)
            .as("Shell config should preserve existing content")
            .contains("# My custom shell config")
            .as("Shell config should contain source command for CLI env")
            .contains("source " + userHome.resolve(BIN_DIR).resolve(envFile))
            .as("Shell config should contain CLI marker comment")
            .contains(CLI_MARKER_COMMENT.trim());
    }
}
