package io.apicurio.registry.cli;

import io.apicurio.registry.cli.config.Config;
import io.apicurio.registry.cli.utils.OutputBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the InstallCommand.
 *
 * <p>These tests verify the installation process including file copying,
 * symlink creation, and shell configuration updates on both macOS (zsh)
 * and Linux (bash) environments.
 *
 * <p>Tests automatically adapt to the running OS and verify OS-specific
 * behavior without requiring Docker or external dependencies.
 */
public class InstallCommandIntegrationTest {

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
        Files.writeString(acrHome.resolve("acr"), "#!/bin/bash\necho 'acr'");
        Files.writeString(acrHome.resolve("acr.jar"), "fake jar content");
        Files.writeString(acrHome.resolve("acr_bash_completions"), "# bash completions");
        Files.writeString(acrHome.resolve("acr_bash_env"), "export ACR_HOME=\"{{ACR_HOME}}\"");
        Files.writeString(acrHome.resolve("acr_zsh_completions"), "# zsh completions");
        Files.writeString(acrHome.resolve("acr_zsh_env"), "export ACR_HOME=\"{{ACR_HOME}}\"");
        Files.writeString(acrHome.resolve("README.md"), "# README");
        Files.writeString(acrHome.resolve("config.json"), "{}");

        // Set up config to point to our test acr-home
        Config.getInstance().setAcrCurrentHomePath(acrHome);
    }

    @AfterEach
    public void tearDown() {
        // Cleanup is handled by @TempDir
    }

    @Test
    public void testInstallCreatesAllNecessaryFiles() throws Exception {
        // Given: Environment variables set for installation
        final String osName = System.getProperty("os.name").toLowerCase();
        final boolean isMacOS = osName.contains("mac") || osName.contains("darwin");

        // Create shell config file (simulating existing user environment)
        final Path shellConfig = isMacOS ? userHome.resolve(".zshrc") : userHome.resolve(".bashrc");
        Files.writeString(shellConfig, "# existing config\n");

        // When: Install command is executed
        final InstallCommand command = new TestableInstallCommand(userHome.toString(), installPath.toString());
        final TestOutputBuffer output = TestOutputBuffer.create();
        command.run(output);

        // Then: Verify common files were copied
        assertThat(installPath.resolve("acr"))
            .as("ACR script should be copied to install directory")
            .exists();
        assertThat(installPath.resolve("acr.jar"))
            .as("ACR JAR should be copied to install directory")
            .exists();
        assertThat(installPath.resolve("README.md"))
            .as("README should be copied to install directory")
            .exists();
        assertThat(installPath.resolve("config.json"))
            .as("Config JSON should be copied to install directory")
            .exists();

        // Verify shell-specific files were copied
        if (isMacOS) {
            assertThat(installPath.resolve("acr_zsh_completions"))
                .as("Zsh completions should be copied on macOS")
                .exists();
            assertThat(installPath.resolve("acr_zsh_env"))
                .as("Zsh environment file should be copied on macOS")
                .exists();

            // Verify ACR_HOME placeholder was replaced
            final String zshEnvContent = Files.readString(installPath.resolve("acr_zsh_env"));
            assertThat(zshEnvContent)
                .as("ACR_HOME placeholder should be replaced with actual path in zsh env file")
                .contains("export ACR_HOME=\"" + installPath.toAbsolutePath() + "\"")
                .doesNotContain("{{ACR_HOME}}");
        } else {
            assertThat(installPath.resolve("acr_bash_completions"))
                .as("Bash completions should be copied on Linux")
                .exists();
            assertThat(installPath.resolve("acr_bash_env"))
                .as("Bash environment file should be copied on Linux")
                .exists();

            // Verify ACR_HOME placeholder was replaced
            final String bashEnvContent = Files.readString(installPath.resolve("acr_bash_env"));
            assertThat(bashEnvContent)
                .as("ACR_HOME placeholder should be replaced with actual path in bash env file")
                .contains("export ACR_HOME=\"" + installPath.toAbsolutePath() + "\"")
                .doesNotContain("{{ACR_HOME}}");
        }

        // Verify installation success message
        assertThat(output.getStdOut())
            .as("Installation should output success message")
            .contains("Installation complete");
    }

    @Test
    public void testInstallCreatesSymlinks() throws Exception {
        // Given: Environment variables set for installation
        final String osName = System.getProperty("os.name").toLowerCase();
        final boolean isMacOS = osName.contains("mac") || osName.contains("darwin");

        // Create shell config file
        final Path shellConfig = isMacOS ? userHome.resolve(".zshrc") : userHome.resolve(".bashrc");
        Files.writeString(shellConfig, "# existing config\n");

        // When: Install command is executed
        final InstallCommand command = new TestableInstallCommand(userHome.toString(), installPath.toString());
        final TestOutputBuffer output = TestOutputBuffer.create();
        command.run(output);

        // Then: Verify symlinks were created in ~/bin
        final Path binPath = userHome.resolve("bin");
        assertThat(binPath)
            .as("~/bin directory should be created")
            .exists();
        assertThat(binPath.resolve("acr"))
            .as("ACR symlink should be created in ~/bin")
            .exists();

        if (isMacOS) {
            assertThat(binPath.resolve("acr_zsh_env"))
                .as("Zsh env symlink should be created in ~/bin on macOS")
                .exists();
        } else {
            assertThat(binPath.resolve("acr_bash_env"))
                .as("Bash env symlink should be created in ~/bin on Linux")
                .exists();
        }
    }

    @Test
    public void testInstallUpdatesShellConfiguration() throws Exception {
        // Given: Environment variables set for installation
        final String osName = System.getProperty("os.name").toLowerCase();
        final boolean isMacOS = osName.contains("mac") || osName.contains("darwin");

        // Create shell config file with existing content
        final Path shellConfig = isMacOS ? userHome.resolve(".zshrc") : userHome.resolve(".bashrc");
        Files.writeString(shellConfig, "# My custom shell config\nexport PATH=/custom/path:$PATH\n");

        // When: Install command is executed
        final InstallCommand command = new TestableInstallCommand(userHome.toString(), installPath.toString());
        final TestOutputBuffer output = TestOutputBuffer.create();
        command.run(output);

        // Then: Verify shell config was updated
        final String shellConfigContent = Files.readString(shellConfig);
        final String shellEnvFile = isMacOS ? "acr_zsh_env" : "acr_bash_env";
        assertThat(shellConfigContent)
            .as("Shell config should preserve existing content")
            .contains("# My custom shell config")
            .as("Shell config should contain source command for CLI env")
            .contains("source " + userHome.resolve("bin").resolve(shellEnvFile))
            .as("Shell config should contain CLI marker comment")
            .contains("# Apicurio Registry CLI");
    }

    @Test
    public void testInstallDoesNotDuplicateShellConfiguration() throws Exception {
        // Given: Environment variables set for installation
        final String osName = System.getProperty("os.name").toLowerCase();
        final boolean isMacOS = osName.contains("mac") || osName.contains("darwin");

        // Create shell config file
        final Path shellConfig = isMacOS ? userHome.resolve(".zshrc") : userHome.resolve(".bashrc");
        Files.writeString(shellConfig, "# existing config\n");

        // When: Install command is executed twice
        final InstallCommand command1 = new TestableInstallCommand(userHome.toString(), installPath.toString());
        final TestOutputBuffer output1 = TestOutputBuffer.create();
        command1.run(output1);

        final InstallCommand command2 = new TestableInstallCommand(userHome.toString(), installPath.toString());
        final TestOutputBuffer output2 = TestOutputBuffer.create();
        command2.run(output2);

        // Then: Verify shell config contains source line only once
        final String shellConfigContent = Files.readString(shellConfig);
        final String shellEnvFile = isMacOS ? "acr_zsh_env" : "acr_bash_env";
        final String sourceCommand = "source " + userHome.resolve("bin").resolve(shellEnvFile);

        final int occurrences = shellConfigContent.split(sourceCommand, -1).length - 1;
        assertThat(occurrences)
            .as("Shell config should contain source command exactly once (idempotent installation)")
            .isEqualTo(1);
    }

    @Test
    public void testInstallCreatesBinDirectoryIfMissing() throws Exception {
        // Given: Environment variables set for installation
        final String osName = System.getProperty("os.name").toLowerCase();
        final boolean isMacOS = osName.contains("mac") || osName.contains("darwin");

        // Create shell config file
        final Path shellConfig = isMacOS ? userHome.resolve(".zshrc") : userHome.resolve(".bashrc");
        Files.writeString(shellConfig, "# existing config\n");

        final Path binPath = userHome.resolve("bin");
        assertThat(binPath)
            .as("~/bin should not exist initially")
            .doesNotExist();

        // When: Install command is executed
        final InstallCommand command = new TestableInstallCommand(userHome.toString(), installPath.toString());
        final TestOutputBuffer output = TestOutputBuffer.create();
        command.run(output);

        // Then: Verify bin directory was created
        assertThat(binPath)
            .as("~/bin directory should be created during installation")
            .exists()
            .isDirectory();
        assertThat(output.getStdOut())
            .as("Installation should warn user about creating ~/bin directory")
            .contains("Created bin directory");
    }

    @Test
    public void testInstallPreservesExistingConfigJson() throws Exception {
        // Given: Existing installation with custom config.json
        final String osName = System.getProperty("os.name").toLowerCase();
        final boolean isMacOS = osName.contains("mac") || osName.contains("darwin");

        // Create shell config file
        final Path shellConfig = isMacOS ? userHome.resolve(".zshrc") : userHome.resolve(".bashrc");
        Files.writeString(shellConfig, "# existing config\n");

        // Create existing config.json with custom content
        Files.createDirectories(installPath);
        final String customConfig = "{\"custom\": \"settings\"}";
        Files.writeString(installPath.resolve("config.json"), customConfig);

        // When: Install command is executed
        final InstallCommand command = new TestableInstallCommand(userHome.toString(), installPath.toString());
        final TestOutputBuffer output = TestOutputBuffer.create();
        command.run(output);

        // Then: Verify config.json was NOT overwritten
        final String configContent = Files.readString(installPath.resolve("config.json"));
        assertThat(configContent)
            .as("Existing config.json should be preserved (not overwritten)")
            .isEqualTo(customConfig);
    }

    /**
     * Testable version of InstallCommand that allows injection of environment variables.
     * This is necessary because we cannot modify System.getenv() in tests.
     */
    private static class TestableInstallCommand extends InstallCommand {
        private final String homeValue;
        private final String installPathValue;

        public TestableInstallCommand(final String homeValue, final String installPathValue) {
            this.homeValue = homeValue;
            this.installPathValue = installPathValue;
        }

        // Override to inject test environment variables
        @Override
        protected String getEnv(final String name) {
            if ("HOME".equals(name)) {
                return homeValue;
            } else if ("ACR_INSTALL_PATH".equals(name)) {
                return installPathValue;
            } else if ("ACR_HOME".equals(name)) {
                return null; // Not set for fresh install
            }
            return System.getenv(name);
        }
    }

    /**
     * Simple implementation of Output for testing that captures output to a StringBuilder.
     */
    private static class TestOutput implements io.apicurio.registry.cli.utils.Output {
        private final StringBuilder content = new StringBuilder();

        @Override
        public void print(final String value) {
            content.append(value);
        }

        public String getContent() {
            return content.toString();
        }
    }

    /**
     * Test OutputBuffer that allows access to captured output.
     */
    private static class TestOutputBuffer extends OutputBuffer {
        private static TestOutput createTestOutput() {
            return new TestOutput();
        }

        private final TestOutput stdOutCapture;
        private final TestOutput stdErrCapture;

        public TestOutputBuffer(final TestOutput stdOutCapture, final TestOutput stdErrCapture) {
            super(stdOutCapture, stdErrCapture);
            this.stdOutCapture = stdOutCapture;
            this.stdErrCapture = stdErrCapture;
        }

        public static TestOutputBuffer create() {
            final TestOutput stdout = createTestOutput();
            final TestOutput stderr = createTestOutput();
            return new TestOutputBuffer(stdout, stderr);
        }

        public String getStdOut() {
            // Print chunks to outputs first
            print();
            return stdOutCapture.getContent();
        }

        public String getStdErr() {
            // Print chunks to outputs first
            print();
            return stdErrCapture.getContent();
        }
    }
}
