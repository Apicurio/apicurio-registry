package io.apicurio.registry.cli;

import io.apicurio.registry.cli.common.CliException;
import io.apicurio.registry.cli.config.Config;
import io.apicurio.registry.cli.config.ConfigModel;
import io.apicurio.registry.cli.utils.Mapper;
import io.apicurio.registry.cli.utils.PlatformUtils;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static io.apicurio.registry.cli.InstallCommand.ACR_BINARY;
import static io.apicurio.registry.cli.InstallCommand.ACR_BINARY_WINDOWS;
import static io.apicurio.registry.cli.InstallCommand.ACR_ENV;
import static io.apicurio.registry.cli.InstallCommand.ACR_HOME_PLACEHOLDER;
import static io.apicurio.registry.cli.InstallCommand.ACR_SCRIPT;
import static io.apicurio.registry.cli.InstallCommand.ACR_SCRIPT_WINDOWS;
import static io.apicurio.registry.cli.InstallCommand.BACKUP_SUFFIX;
import static io.apicurio.registry.cli.InstallCommand.BIN_DIR;
import static io.apicurio.registry.cli.InstallCommand.CLI_MARKER_COMMENT;
import static io.apicurio.registry.cli.InstallCommand.COMPLETIONS;
import static io.apicurio.registry.cli.InstallCommand.CONFIG_JSON;
import static io.apicurio.registry.cli.InstallCommand.CONFIG_KEY_GLOBAL_INSTALL;
import static io.apicurio.registry.cli.InstallCommand.ENV_ACR_GLOBAL_BIN;
import static io.apicurio.registry.cli.InstallCommand.ENV_ACR_GLOBAL_HOME;
import static io.apicurio.registry.cli.InstallCommand.ENV_ACR_HOME;
import static io.apicurio.registry.cli.InstallCommand.ENV_ACR_INSTALL_PATH;
import static io.apicurio.registry.cli.InstallCommand.ENV_HOME;
import static io.apicurio.registry.cli.InstallCommand.ENV_PATH;
import static io.apicurio.registry.cli.InstallCommand.README;
import static io.apicurio.registry.cli.InstallCommand.getAcrBinaryName;
import static io.apicurio.registry.cli.InstallCommand.getAcrScriptName;
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

    @Inject
    TestUserEnvironment userEnvironment;

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

        // Create required files in acr-home (simulating distribution ZIP contents). The Windows
        // ZIP ships acr.cmd and acr_runner.exe, and has no acr_env to source.
        Files.writeString(acrHome.resolve(getAcrScriptName()), "#!/bin/bash\necho 'acr'");
        Files.writeString(acrHome.resolve(getAcrBinaryName()), "fake binary content");
        Files.writeString(acrHome.resolve(COMPLETIONS), "# completions");
        if (!PlatformUtils.isWindows()) {
            Files.writeString(acrHome.resolve(ACR_ENV), "export " + ENV_ACR_HOME + "=\"" + ACR_HOME_PLACEHOLDER + "\"");
        }
        Files.writeString(acrHome.resolve(README), "# README");
        Files.writeString(acrHome.resolve(CONFIG_JSON), "{}");

        // Set up config to point to our test acr-home
        config.setAcrCurrentHomePath(acrHome);

        // Set up environment variable overrides for testing
        config.setEnvOverride(ENV_HOME, userHome.toString());
        config.setEnvOverride(ENV_ACR_INSTALL_PATH, installPath.toString());
        config.setEnvOverride(ENV_ACR_HOME, null); // Simulate fresh install

        userEnvironment.reset();
    }

    @AfterEach
    public void tearDown() {
        // Clear environment overrides to avoid test interference
        config.clearEnvOverrides();
        userEnvironment.reset();
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

    // ========== Global (system-wide) installation ==========

    @Test
    @EnabledOnOs({OS.MAC, OS.LINUX})
    public void testGlobalInstallCopiesFilesAndSymlinksIntoGlobalBin() throws Exception {
        final Path[] locations = useTempGlobalLocations();
        final Path globalHome = locations[0];
        final Path globalBin = locations[1];

        runInstallCommand("--global");

        assertThat(globalHome.resolve(ACR_BINARY))
            .as("Binary should be copied to the global home")
            .exists();
        assertThat(globalBin.resolve(ACR_SCRIPT))
            .as("acr launcher should be symlinked into the global bin directory")
            .exists();
        assertThat(globalBin.resolve(ACR_ENV))
            .as("acr_env should be symlinked into the global bin directory")
            .exists();
    }

    @Test
    @EnabledOnOs({OS.MAC, OS.LINUX})
    public void testGlobalInstallMarksConfigAsGlobal() throws Exception {
        final Path globalHome = useTempGlobalLocations()[0];

        runInstallCommand("--global");

        final ConfigModel model = Mapper.MAPPER.readValue(globalHome.resolve(CONFIG_JSON).toFile(), ConfigModel.class);
        assertThat(model.getConfig().get(CONFIG_KEY_GLOBAL_INSTALL))
            .as("A global install should record the global marker in config.json (used by 'acr update')")
            .isEqualTo("true");
    }

    @Test
    @EnabledOnOs({OS.MAC, OS.LINUX})
    public void testGlobalInstallDoesNotTouchUserHomeOrShellConfig() throws Exception {
        final Path shellConfig = createShellConfigFile("# My custom shell config\n");
        useTempGlobalLocations();

        runInstallCommand("--global");

        assertThat(userHome.resolve(BIN_DIR))
            .as("A global install must not create the per-user ~/bin directory")
            .doesNotExist();
        assertThat(Files.readString(shellConfig))
            .as("A global install must not modify the user's shell configuration")
            .doesNotContain(ACR_ENV);
    }

    @Test
    @EnabledOnOs({OS.MAC, OS.LINUX})
    public void testGlobalInstallWithoutPrivilegesFailsWithClearError() throws Exception {
        // The privilege check relies on the process not being able to write to the target dir, which
        // is not true for root, so skip there rather than assert a check that cannot fire.
        Assumptions.assumeFalse("root".equals(System.getProperty("user.name")),
                "Privilege check cannot be exercised as root");

        final Path globalHome = tempDir.resolve("global-home");
        final Path readOnlyBin = tempDir.resolve("readonly-bin");
        Files.createDirectories(readOnlyBin);
        assertThat(readOnlyBin.toFile().setWritable(false, false))
            .as("test setup: the bin directory must be made read-only")
            .isTrue();

        config.setEnvOverride(ENV_ACR_GLOBAL_HOME, globalHome.toString());
        config.setEnvOverride(ENV_ACR_GLOBAL_BIN, readOnlyBin.toString());

        final StringBuilder err = new StringBuilder();
        final var originalErr = config.getStdErr();
        config.setStdErr(err::append);
        try {
            final int exitCode = new CommandLine(new Acr(), factory).execute("install", "--global");

            assertThat(exitCode)
                .as("Install without write access should return the validation error code")
                .isEqualTo(CliException.VALIDATION_ERROR_RETURN_CODE);
            assertThat(err.toString())
                .as("The error should tell the user to re-run with sudo")
                .contains("sudo");
            assertThat(globalHome)
                .as("Nothing should be installed when the privilege check fails")
                .doesNotExist();
        } finally {
            config.setStdErr(originalErr);
            readOnlyBin.toFile().setWritable(true, false);
        }
    }

    @Test
    @EnabledOnOs({OS.MAC, OS.LINUX})
    public void testUserInstallDoesNotSetGlobalMarker() throws Exception {
        createShellConfigFile("# existing config\n");

        runInstallCommand();

        final ConfigModel model = Mapper.MAPPER.readValue(installPath.resolve(CONFIG_JSON).toFile(), ConfigModel.class);
        assertThat(model.getConfig().get(CONFIG_KEY_GLOBAL_INSTALL))
            .as("A per-user install must not set the global marker")
            .isNull();
    }

    @Test
    @EnabledOnOs({OS.MAC, OS.LINUX})
    public void testInstallRollsBackFilesWhenCopyFails() throws Exception {
        createShellConfigFile("# existing config\n");

        // Simulate an existing, working installation at the target location.
        Files.createDirectories(installPath);
        final String oldScript = "#!/bin/bash\necho 'old acr'";
        final String oldBinary = "old binary content";
        final String oldReadme = "# old readme";
        final String oldCompletions = "# old completions";
        Files.writeString(installPath.resolve(ACR_SCRIPT), oldScript);
        Files.writeString(installPath.resolve(ACR_BINARY), oldBinary);
        Files.writeString(installPath.resolve(README), oldReadme);
        Files.writeString(installPath.resolve(COMPLETIONS), oldCompletions);

        // Break the distribution so the copy fails after several files have been replaced.
        Files.delete(acrHome.resolve(ACR_ENV));

        final var acr = new Acr();
        final var cmd = new CommandLine(acr, factory);
        final int exitCode = cmd.execute("install");

        assertThat(exitCode)
            .as("Install should fail when the distribution is incomplete")
            .isNotEqualTo(0);
        // Every file overwritten before the failure should be rolled back to its previous version.
        assertThat(Files.readString(installPath.resolve(ACR_SCRIPT))).isEqualTo(oldScript);
        assertThat(Files.readString(installPath.resolve(ACR_BINARY))).isEqualTo(oldBinary);
        assertThat(Files.readString(installPath.resolve(README))).isEqualTo(oldReadme);
        assertThat(Files.readString(installPath.resolve(COMPLETIONS))).isEqualTo(oldCompletions);
        // Backup files should be cleaned up after rollback.
        for (final String name : new String[] {ACR_SCRIPT, ACR_BINARY, README, COMPLETIONS}) {
            assertThat(installPath.resolve(name + BACKUP_SUFFIX))
                .as(name + " backup should be removed after rollback")
                .doesNotExist();
        }
    }

    // ========== Windows ==========

    @Test
    @EnabledOnOs(OS.WINDOWS)
    public void testInstallCreatesAllNecessaryFilesOnWindows() throws Exception {
        runInstallCommand();

        assertThat(installPath.resolve(ACR_SCRIPT_WINDOWS))
            .as("The cmd launcher should be copied to the install directory")
            .isRegularFile();
        assertThat(installPath.resolve(ACR_BINARY_WINDOWS))
            .as("The .exe binary should be copied to the install directory")
            .isRegularFile();
        assertThat(installPath.resolve(README)).isRegularFile();
        assertThat(installPath.resolve(CONFIG_JSON)).isRegularFile();
        assertThat(installPath.resolve(COMPLETIONS)).isRegularFile();
        assertThat(installPath.resolve(ACR_ENV))
            .as("There is no shell to source acr_env on Windows, so it should not be installed")
            .doesNotExist();
        assertThat(installPath.resolve(ACR_SCRIPT))
            .as("The POSIX launcher should not be installed on Windows")
            .doesNotExist();
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    public void testInstallCopiesLauncherIntoBinDirectoryOnWindows() throws Exception {
        runInstallCommand();

        final Path launcher = userHome.resolve(BIN_DIR).resolve(ACR_SCRIPT_WINDOWS);
        assertThat(launcher)
            .as("The launcher is copied rather than symlinked, which needs Developer Mode on Windows")
            .isRegularFile();
        assertThat(Files.isSymbolicLink(launcher)).isFalse();
        assertThat(Files.readString(launcher)).isEqualTo(Files.readString(acrHome.resolve(ACR_SCRIPT_WINDOWS)));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    public void testInstallPersistsUserEnvironmentOnWindows() throws Exception {
        runInstallCommand();

        assertThat(userEnvironment.getUserVariable(ENV_ACR_HOME))
            .as("ACR_HOME should point at the install directory so the launcher can find the binary")
            .isEqualTo(installPath.toAbsolutePath().toString());
        assertThat(userEnvironment.getUserVariable(ENV_PATH))
            .as("The bin directory should be prepended to the user Path")
            .isEqualTo(userHome.resolve(BIN_DIR).toString());
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    public void testInstallPrependsToExistingUserPathOnWindows() throws Exception {
        userEnvironment.seed(ENV_PATH, "C:\\Existing;C:\\Other");

        runInstallCommand();

        assertThat(userEnvironment.getUserVariable(ENV_PATH))
            .as("The existing user Path entries should be preserved")
            .isEqualTo(userHome.resolve(BIN_DIR) + ";C:\\Existing;C:\\Other");
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    public void testInstallDoesNotDuplicateUserPathEntryOnWindows() throws Exception {
        runInstallCommand();
        final String pathAfterFirstInstall = userEnvironment.getUserVariable(ENV_PATH);

        runInstallCommand();

        assertThat(userEnvironment.getUserVariable(ENV_PATH))
            .as("A second install should not add the bin directory to the Path again")
            .isEqualTo(pathAfterFirstInstall);
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    public void testInstallMatchesExistingPathEntryIgnoringCaseAndTrailingSeparatorOnWindows() throws Exception {
        // Windows paths are case-insensitive, and a trailing backslash names the same directory.
        final String binDirectory = userHome.resolve(BIN_DIR).toString();
        userEnvironment.seed(ENV_PATH, binDirectory.toUpperCase(Locale.ROOT) + "\\;C:\\Other");

        runInstallCommand();

        assertThat(userEnvironment.getUserVariable(ENV_PATH))
            .as("An entry differing only in case or a trailing separator should not be added again")
            .isEqualTo(binDirectory.toUpperCase(Locale.ROOT) + "\\;C:\\Other");
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    public void testInstallDoesNotTouchShellConfigOnWindows() throws Exception {
        final String existingContent = "# My custom shell config\n";
        final Path shellConfig = createShellConfigFile(existingContent);

        runInstallCommand();

        assertThat(Files.readString(shellConfig))
            .as("Windows persists environment variables instead of editing a shell config file")
            .isEqualTo(existingContent);
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    public void testInstallReplacesExistingBinaryOnWindows() throws Exception {
        // The binary is renamed out of the way before being overwritten, because Windows locks
        // the image of a running executable. Verify the replacement completes and leaves no
        // backup files behind.
        Files.createDirectories(installPath);
        Files.writeString(installPath.resolve(ACR_SCRIPT_WINDOWS), "@echo old launcher");
        Files.writeString(installPath.resolve(ACR_BINARY_WINDOWS), "old binary content");

        runInstallCommand();

        assertThat(Files.readString(installPath.resolve(ACR_BINARY_WINDOWS)))
            .isEqualTo(Files.readString(acrHome.resolve(ACR_BINARY_WINDOWS)));
        assertThat(Files.readString(installPath.resolve(ACR_SCRIPT_WINDOWS)))
            .isEqualTo(Files.readString(acrHome.resolve(ACR_SCRIPT_WINDOWS)));
        assertThat(installPath.resolve(ACR_BINARY_WINDOWS + BACKUP_SUFFIX))
            .as("The backup should be removed once the install succeeds")
            .doesNotExist();
        assertThat(installPath.resolve(ACR_SCRIPT_WINDOWS + BACKUP_SUFFIX)).doesNotExist();
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    public void testInstallPreservesExistingConfigJsonOnWindows() throws Exception {
        Files.createDirectories(installPath);
        final String customConfig = "{\"custom\": \"settings\"}";
        Files.writeString(installPath.resolve(CONFIG_JSON), customConfig);

        runInstallCommand();

        assertThat(Files.readString(installPath.resolve(CONFIG_JSON)))
            .as("Existing " + CONFIG_JSON + " should be preserved (not overwritten)")
            .isEqualTo(customConfig);
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    public void testInstallRollsBackFilesWhenCopyFailsOnWindows() throws Exception {
        // Simulate an existing, working installation at the target location.
        Files.createDirectories(installPath);
        final String oldLauncher = "@echo old launcher";
        final String oldBinary = "old binary content";
        final String oldReadme = "# old readme";
        final String oldCompletions = "# old completions";
        Files.writeString(installPath.resolve(ACR_SCRIPT_WINDOWS), oldLauncher);
        Files.writeString(installPath.resolve(ACR_BINARY_WINDOWS), oldBinary);
        Files.writeString(installPath.resolve(README), oldReadme);
        Files.writeString(installPath.resolve(COMPLETIONS), oldCompletions);

        // Break the distribution so the copy fails after the launcher, binary and README have
        // already been replaced.
        Files.delete(acrHome.resolve(COMPLETIONS));

        final var acr = new Acr();
        final var cmd = new CommandLine(acr, factory);
        final int exitCode = cmd.execute("install");

        assertThat(exitCode)
            .as("Install should fail when the distribution is incomplete")
            .isNotEqualTo(0);
        // The renamed launcher and binary, and the copied README, should all be back in place.
        assertThat(Files.readString(installPath.resolve(ACR_SCRIPT_WINDOWS))).isEqualTo(oldLauncher);
        assertThat(Files.readString(installPath.resolve(ACR_BINARY_WINDOWS))).isEqualTo(oldBinary);
        assertThat(Files.readString(installPath.resolve(README))).isEqualTo(oldReadme);
        assertThat(Files.readString(installPath.resolve(COMPLETIONS))).isEqualTo(oldCompletions);
        for (final String name : new String[] {ACR_SCRIPT_WINDOWS, ACR_BINARY_WINDOWS, README, COMPLETIONS}) {
            assertThat(installPath.resolve(name + BACKUP_SUFFIX))
                .as(name + " backup should be removed after rollback")
                .doesNotExist();
        }
    }

    // ========== Helper Methods ==========

    private Path createShellConfigFile(final String content) throws IOException {
        final Path shellConfig = userHome.resolve(getShellConfigFile());
        Files.writeString(shellConfig, content);
        return shellConfig;
    }

    private void runInstallCommand(final String... extraArgs) throws IOException {
        var acr = new Acr();
        var cmd = new CommandLine(acr, factory);
        var args = new java.util.ArrayList<String>();
        args.add("install");
        java.util.Collections.addAll(args, extraArgs);
        cmd.execute(args.toArray(new String[0]));
    }

    /**
     * Points the global install locations at writable temp directories so the system-wide install
     * path can be exercised without touching real system directories or needing root.
     *
     * @return the [globalHome, globalBin] paths configured for the test
     */
    private Path[] useTempGlobalLocations() throws IOException {
        final Path globalHome = tempDir.resolve("global-home");
        final Path globalBin = tempDir.resolve("global-bin");
        Files.createDirectories(globalBin);
        config.setEnvOverride(ENV_ACR_GLOBAL_HOME, globalHome.toString());
        config.setEnvOverride(ENV_ACR_GLOBAL_BIN, globalBin.toString());
        return new Path[] { globalHome, globalBin };
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
