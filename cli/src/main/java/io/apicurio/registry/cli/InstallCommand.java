package io.apicurio.registry.cli;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.CliException;
import io.apicurio.registry.cli.config.Config;
import io.apicurio.registry.cli.utils.FileUtils;
import io.apicurio.registry.cli.utils.OutputBuffer;
import org.jboss.logging.Logger;
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

    private static final Logger log = Logger.getLogger(InstallCommand.class);

    // File names
    public static final String ACR_SCRIPT = "acr";
    public static final String ACR_BINARY = "acr_runner";
    public static final String ACR_ENV = "acr_env";
    public static final String README = "README.md";
    public static final String CONFIG_JSON = "config.json";

    // Shell completions
    public static final String COMPLETIONS = "acr_completions";

    // Shell config files
    public static final String BASHRC = ".bashrc";
    public static final String ZSHRC = ".zshrc";

    // Directory names
    public static final String BIN_DIR = "bin";

    // Placeholders and markers
    public static final String ACR_HOME_PLACEHOLDER = "{{ACR_HOME}}";
    public static final String CLI_MARKER_COMMENT = " # Apicurio Registry CLI";

    // Environment variable names
    public static final String ENV_ACR_INSTALL_PATH = "ACR_INSTALL_PATH";
    public static final String ENV_ACR_HOME = "ACR_HOME";
    public static final String ENV_HOME = "HOME";

    // OS detection
    public static final String OS_NAME_PROPERTY = "os.name";
    public static final String OS_MAC_IDENTIFIER = "mac";
    public static final String OS_DARWIN_IDENTIFIER = "darwin";

    @Override
    public void run(final OutputBuffer output) throws IOException {
        // Location of the directory where the current CLI binary is running from
        final Path currentPath = Config.getInstance().getAcrCurrentHomePath();
        log.debugf("Current home path: %s", currentPath);

        final Path cliHomePath = determineCliHomePath();

        copyFiles(currentPath, cliHomePath);
        final Path userHomePath = getUserHomePath();
        final Path binPath = ensureBinDirectoryExists(userHomePath, output);

        createSymlinks(binPath, cliHomePath);
        final Path shellConfigPath = updateShellConfiguration(userHomePath, binPath);

        output.writeStdOutLine("Installation complete. Please restart your terminal or run `source " + shellConfigPath + "`.");
    }

    /**
     * Determines the CLI home path where files will be installed.
     * Uses ACR_HOME if set and valid, otherwise uses ACR_INSTALL_PATH.
     */
    private Path determineCliHomePath() throws IOException {
        // Default home directory, where the CLI should be installed
        final String installDir = Config.getInstance().getEnv(ENV_ACR_INSTALL_PATH);
        if (isBlank(installDir)) {
            throw new CliException("Environment variable " + ENV_ACR_INSTALL_PATH + " is not set.", VALIDATION_ERROR_RETURN_CODE);
        }
        log.debugf("%s=%s", ENV_ACR_INSTALL_PATH, installDir);
        final Path installDirPath = Paths.get(installDir).normalize().toAbsolutePath();

        // Location of the CLI home directory, set only if the CLI is already installed
        final String cliHome = Config.getInstance().getEnv(ENV_ACR_HOME);
        log.debugf("%s=%s", ENV_ACR_HOME, cliHome);
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
            log.debugf("Created directory: %s", installDirPath);
            cliHomePath = installDirPath;
        }

        return cliHomePath;
    }

    /**
     * Detects if the current OS is macOS.
     *
     * @return true if running on macOS, false otherwise
     */
    public static boolean detectMacOS() {
        final String osName = System.getProperty(OS_NAME_PROPERTY).toLowerCase();
        return osName.contains(OS_MAC_IDENTIFIER) || osName.contains(OS_DARWIN_IDENTIFIER);
    }

    /**
     * Gets the appropriate shell configuration file name for the current OS.
     *
     * @return ZSHRC for macOS, BASHRC for Linux
     */
    public static String getShellConfigFile() {
        return detectMacOS() ? ZSHRC : BASHRC;
    }

    /**
     * Copies all necessary files to the CLI home directory.
     * The distribution ZIP already contains the correct OS-specific acr_env file,
     * so no OS detection is needed for file selection.
     */
    private void copyFiles(final Path currentPath, final Path cliHomePath) throws IOException {
        // Copy common files
        Files.copy(currentPath.resolve(ACR_SCRIPT), cliHomePath.resolve(ACR_SCRIPT), REPLACE_EXISTING);
        Files.copy(currentPath.resolve(ACR_BINARY), cliHomePath.resolve(ACR_BINARY), REPLACE_EXISTING);
        // Ensure the binary is executable
        cliHomePath.resolve(ACR_BINARY).toFile().setExecutable(true, false);
        Files.copy(currentPath.resolve(README), cliHomePath.resolve(README), REPLACE_EXISTING);

        // Copy completions and shell environment file
        Files.copy(currentPath.resolve(COMPLETIONS), cliHomePath.resolve(COMPLETIONS), REPLACE_EXISTING);
        Files.copy(currentPath.resolve(ACR_ENV), cliHomePath.resolve(ACR_ENV), REPLACE_EXISTING);
        FileUtils.replaceInFile(cliHomePath.resolve(ACR_ENV), ACR_HOME_PLACEHOLDER, cliHomePath.toAbsolutePath().toString());

        // Copy config.json only if it doesn't exist (preserve user settings)
        if (!Files.exists(cliHomePath.resolve(CONFIG_JSON))) {
            Files.copy(currentPath.resolve(CONFIG_JSON), cliHomePath.resolve(CONFIG_JSON));
        }
    }

    /**
     * Gets the user's home directory path.
     */
    private Path getUserHomePath() {
        final String userHome = Config.getInstance().getEnv(ENV_HOME);
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
    private void createSymlinks(final Path binPath, final Path cliHomePath) throws IOException {
        FileUtils.createLink(binPath.resolve(ACR_SCRIPT), cliHomePath.resolve(ACR_SCRIPT));
        FileUtils.createLink(binPath.resolve(ACR_ENV), cliHomePath.resolve(ACR_ENV));
    }

    /**
     * Updates the shell configuration file (.bashrc or .zshrc) to source the CLI environment.
     * Returns the path to the shell configuration file.
     */
    private Path updateShellConfiguration(final Path userHomePath, final Path binPath) throws IOException {
        final Path shellConfigPath = userHomePath.resolve(getShellConfigFile());

        if (Files.exists(shellConfigPath)) {
            final String sourceCmd = "source " + binPath.resolve(ACR_ENV);
            if (!FileUtils.findInFile(shellConfigPath, sourceCmd)) {
                try {
                    Files.writeString(shellConfigPath, "\n" + sourceCmd + CLI_MARKER_COMMENT + "\n", StandardOpenOption.APPEND);
                    log.debugf("Updated %s at: %s", shellConfigPath.getFileName(), shellConfigPath);
                } catch (final IOException e) {
                    log.errorf(e, "Failed to update %s at: %s", shellConfigPath.getFileName(), shellConfigPath);
                    throw new RuntimeException(e);
                }
            }
        } else {
            log.warnf("Could not update '%s'. File does not exist at: %s", shellConfigPath.getFileName(), shellConfigPath);
        }

        return shellConfigPath;
    }
}
