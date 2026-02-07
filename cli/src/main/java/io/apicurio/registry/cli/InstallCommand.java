package io.apicurio.registry.cli;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.CliException;
import io.apicurio.registry.cli.config.Config;
import io.apicurio.registry.cli.utils.FileUtils;
import io.apicurio.registry.cli.utils.OutputBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine.Command;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import static io.apicurio.registry.cli.common.CliException.VALIDATION_ERROR_RETURN_CODE;
import static io.apicurio.registry.cli.utils.Utils.isBlank;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

@Command(
        name = "install",
        description = "Install the CLI to the user's home directory and configure shell integration"
)
public class InstallCommand extends AbstractCommand {

    private static final Logger log = LogManager.getRootLogger();

    // File names
    private static final String ACR_SCRIPT = "acr";
    private static final String ACR_JAR = "acr.jar";
    private static final String README = "README.md";
    private static final String CONFIG_JSON = "config.json";

    // Shell-specific files
    private static final String BASH_COMPLETIONS = "acr_bash_completions";
    private static final String BASH_ENV = "acr_bash_env";
    private static final String ZSH_COMPLETIONS = "acr_zsh_completions";
    private static final String ZSH_ENV = "acr_zsh_env";

    // Shell config files
    private static final String BASHRC = ".bashrc";
    private static final String ZSHRC = ".zshrc";

    // Directory names
    private static final String BIN_DIR = "bin";

    // Placeholders and markers
    private static final String ACR_HOME_PLACEHOLDER = "{{ACR_HOME}}";
    private static final String CLI_MARKER_COMMENT = " # Apicurio Registry CLI";

    // Environment variable names
    private static final String ENV_ACR_INSTALL_PATH = "ACR_INSTALL_PATH";
    private static final String ENV_ACR_HOME = "ACR_HOME";
    private static final String ENV_HOME = "HOME";

    // OS detection
    private static final String OS_NAME_PROPERTY = "os.name";
    private static final String OS_MAC_IDENTIFIER = "mac";
    private static final String OS_DARWIN_IDENTIFIER = "darwin";

    /**
     * Gets an environment variable value. Protected to allow test overriding.
     *
     * @param name the environment variable name
     * @return the environment variable value, or null if not set
     */
    protected String getEnv(final String name) {
        return System.getenv(name);
    }

    @Override
    public void run(final OutputBuffer output) throws IOException {
        // Location of the directory where the current CLI .jar is running from
        final Path currentPath = Config.getInstance().getAcrCurrentHomePath();
        log.debug("Current home path: {}", currentPath);

        final Path cliHomePath = determineCliHomePath();
        final boolean isMacOS = detectMacOS();

        copyFiles(currentPath, cliHomePath, isMacOS);
        final Path userHomePath = getUserHomePath();
        final Path binPath = ensureBinDirectoryExists(userHomePath, output);

        createSymlinks(binPath, cliHomePath, isMacOS);
        final Path shellConfigPath = updateShellConfiguration(userHomePath, binPath, isMacOS);

        output.writeStdOutLine("Installation complete. Please restart your terminal or run `source " + shellConfigPath + "`.");
    }

    /**
     * Determines the CLI home path where files will be installed.
     * Uses ACR_HOME if set and valid, otherwise uses ACR_INSTALL_PATH.
     */
    private Path determineCliHomePath() throws IOException {
        // Default home directory, where the CLI should be installed
        final String installDir = getEnv(ENV_ACR_INSTALL_PATH);
        if (isBlank(installDir)) {
            throw new CliException("Environment variable " + ENV_ACR_INSTALL_PATH + " is not set.", VALIDATION_ERROR_RETURN_CODE);
        }
        log.debug("{}={}", ENV_ACR_INSTALL_PATH, installDir);
        final Path installDirPath = Paths.get(installDir).normalize().toAbsolutePath();

        // Location of the CLI home directory, set only if the CLI is already installed
        final String cliHome = getEnv(ENV_ACR_HOME);
        log.debug("{}={}", ENV_ACR_HOME, cliHome);
        Path cliHomePath = null;

        if (!isBlank(cliHome)) {
            cliHomePath = Path.of(cliHome).normalize().toAbsolutePath();
            if (!Files.exists(cliHomePath)) {
                cliHomePath = null;
            }
        }

        // Path to CLI home directory is not set or is invalid, use default install dir
        if (cliHomePath == null) {
            Files.createDirectories(installDirPath);
            log.debug("Created directory: {}", installDirPath);
            cliHomePath = installDirPath;
        }

        return cliHomePath;
    }

    /**
     * Detects if the current OS is macOS.
     */
    private boolean detectMacOS() {
        final String osName = System.getProperty(OS_NAME_PROPERTY).toLowerCase();
        return osName.contains(OS_MAC_IDENTIFIER) || osName.contains(OS_DARWIN_IDENTIFIER);
    }

    /**
     * Copies all necessary files to the CLI home directory.
     */
    private void copyFiles(final Path currentPath, final Path cliHomePath, final boolean isMacOS) throws IOException {
        // Copy common files
        Files.copy(currentPath.resolve(ACR_SCRIPT), cliHomePath.resolve(ACR_SCRIPT), REPLACE_EXISTING);
        Files.copy(currentPath.resolve(ACR_JAR), cliHomePath.resolve(ACR_JAR), REPLACE_EXISTING);
        Files.copy(currentPath.resolve(README), cliHomePath.resolve(README), REPLACE_EXISTING);

        // Copy shell-specific files
        copyShellSpecificFiles(currentPath, cliHomePath, isMacOS);

        // Copy config.json only if it doesn't exist (preserve user settings)
        if (!Files.exists(cliHomePath.resolve(CONFIG_JSON))) {
            Files.copy(currentPath.resolve(CONFIG_JSON), cliHomePath.resolve(CONFIG_JSON));
        }
    }

    /**
     * Copies shell-specific completion and environment files based on OS.
     */
    private void copyShellSpecificFiles(final Path currentPath, final Path cliHomePath, final boolean isMacOS) throws IOException {
        if (isMacOS) {
            Files.copy(currentPath.resolve(ZSH_COMPLETIONS), cliHomePath.resolve(ZSH_COMPLETIONS), REPLACE_EXISTING);
            Files.copy(currentPath.resolve(ZSH_ENV), cliHomePath.resolve(ZSH_ENV), REPLACE_EXISTING);
            FileUtils.replaceInFile(cliHomePath.resolve(ZSH_ENV), ACR_HOME_PLACEHOLDER, cliHomePath.toAbsolutePath().toString());
        } else {
            Files.copy(currentPath.resolve(BASH_COMPLETIONS), cliHomePath.resolve(BASH_COMPLETIONS), REPLACE_EXISTING);
            Files.copy(currentPath.resolve(BASH_ENV), cliHomePath.resolve(BASH_ENV), REPLACE_EXISTING);
            FileUtils.replaceInFile(cliHomePath.resolve(BASH_ENV), ACR_HOME_PLACEHOLDER, cliHomePath.toAbsolutePath().toString());
        }
    }

    /**
     * Gets the user's home directory path.
     */
    private Path getUserHomePath() {
        final String userHome = getEnv(ENV_HOME);
        if (isBlank(userHome)) {
            throw new CliException(ENV_HOME + " environment variable is not set.", VALIDATION_ERROR_RETURN_CODE);
        }
        return Path.of(userHome).normalize().toAbsolutePath();
    }

    /**
     * Ensures the ~/bin directory exists, creating it if necessary.
     */
    private Path ensureBinDirectoryExists(final Path userHomePath, final OutputBuffer output) throws IOException {
        final Path binPath = userHomePath.resolve(BIN_DIR);
        if (!Files.exists(binPath)) {
            Files.createDirectories(binPath);
            output.writeStdOutLine("Created bin directory at '" + binPath + "'. " +
                    "Make sure your system is configured to look for executable files in this directory.");
        }
        return binPath;
    }

    /**
     * Creates symlinks for the CLI executable and shell environment files.
     */
    private void createSymlinks(final Path binPath, final Path cliHomePath, final boolean isMacOS) throws IOException {
        FileUtils.createLink(binPath.resolve(ACR_SCRIPT), cliHomePath.resolve(ACR_SCRIPT));

        if (isMacOS) {
            FileUtils.createLink(binPath.resolve(ZSH_ENV), cliHomePath.resolve(ZSH_ENV));
        } else {
            FileUtils.createLink(binPath.resolve(BASH_ENV), cliHomePath.resolve(BASH_ENV));
        }
    }

    /**
     * Updates the shell configuration file (.bashrc or .zshrc) to source the CLI environment.
     * Returns the path to the shell configuration file.
     */
    private Path updateShellConfiguration(final Path userHomePath, final Path binPath, final boolean isMacOS) throws IOException {
        final String shellEnvFile = isMacOS ? ZSH_ENV : BASH_ENV;
        final Path shellConfigPath = isMacOS ? userHomePath.resolve(ZSHRC) : userHomePath.resolve(BASHRC);

        if (Files.exists(shellConfigPath)) {
            final String sourceCmd = "source " + binPath.resolve(shellEnvFile);
            if (!FileUtils.findInFile(shellConfigPath, sourceCmd)) {
                try {
                    Files.writeString(shellConfigPath, "\n" + sourceCmd + CLI_MARKER_COMMENT + "\n", StandardOpenOption.APPEND);
                    log.debug("Updated {} at: {}", shellConfigPath.getFileName(), shellConfigPath);
                } catch (final IOException e) {
                    log.error("Failed to update {} at: {}", shellConfigPath.getFileName(), shellConfigPath, e);
                    throw new RuntimeException(e);
                }
            }
        } else {
            log.warn("Could not update '{}'. File does not exist at: {}", shellConfigPath.getFileName(), shellConfigPath);
        }

        return shellConfigPath;
    }
}
