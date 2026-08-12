package io.apicurio.registry.cli;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.CliException;
import io.apicurio.registry.cli.config.ConfigModel;
import io.apicurio.registry.cli.utils.FileUtils;
import io.apicurio.registry.cli.utils.Mapper;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.cli.utils.PlatformUtils;
import io.apicurio.registry.cli.utils.UserEnvironment;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.jboss.logging.Logger;
import picocli.CommandLine.Command;

import static io.apicurio.registry.cli.common.CliException.VALIDATION_ERROR_RETURN_CODE;
import static io.apicurio.registry.cli.utils.Utils.isBlank;
import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

@Command(
        name = "install",
        description = "Install the CLI to the user's home directory and configure shell integration"
)
public class InstallCommand extends AbstractCommand {

    private static final Logger log = Logger.getLogger(InstallCommand.class);

    // File names
    public static final String ACR_SCRIPT = "acr";
    public static final String ACR_SCRIPT_WINDOWS = "acr.cmd";
    public static final String ACR_BINARY = "acr_runner";
    public static final String ACR_BINARY_WINDOWS = "acr_runner.exe";
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

    // Suffix used for the backup copies kept while replacing an existing installation.
    public static final String BACKUP_SUFFIX = ".bak";

    // Placeholders and markers
    public static final String ACR_HOME_PLACEHOLDER = "{{ACR_HOME}}";
    public static final String CLI_MARKER_COMMENT = " # Apicurio Registry CLI";

    // Environment variable names
    public static final String ENV_ACR_INSTALL_PATH = "ACR_INSTALL_PATH";
    public static final String ENV_ACR_HOME = "ACR_HOME";
    public static final String ENV_HOME = "HOME";
    public static final String ENV_USERPROFILE = "USERPROFILE";

    // Name of the user-scope variable holding the directories searched for executables on Windows.
    public static final String ENV_PATH = "Path";
    public static final String PATH_SEPARATOR = ";";

    @Inject
    UserEnvironment userEnvironment;

    @Override
    public void run(final OutputBuffer output) throws IOException {
        // Location of the directory where the current CLI binary is running from
        final Path currentPath = config.getAcrCurrentHomePath();
        log.debugf("Current home path: %s", currentPath);

        final Path cliHomePath = determineCliHomePath();

        copyFiles(currentPath, cliHomePath);
        final Path userHomePath = getUserHomePath();
        final Path binPath = ensureBinDirectoryExists(userHomePath, output);

        if (PlatformUtils.isWindows()) {
            // Windows has neither symlinks without elevation nor a shell file to source, so the
            // launcher is copied and the environment is persisted for the user instead.
            copyLauncherToBinDirectory(binPath, cliHomePath);
            updateUserEnvironment(cliHomePath, binPath);
            output.writeStdOutLine(
                    "Installation complete. Open a new terminal for the environment changes to take effect.");
            return;
        }

        createSymlinks(binPath, cliHomePath);
        final Path shellConfigPath = updateShellConfiguration(userHomePath, binPath);

        output.writeStdOutLine("Installation complete. Please restart your terminal or run `source " + shellConfigPath + "`.");
    }

    /**
     * Name of the launcher script for the current OS, as shipped in the distribution ZIP.
     */
    public static String getAcrScriptName() {
        return PlatformUtils.isWindows() ? ACR_SCRIPT_WINDOWS : ACR_SCRIPT;
    }

    /**
     * Name of the native binary for the current OS, as shipped in the distribution ZIP.
     */
    public static String getAcrBinaryName() {
        return PlatformUtils.isWindows() ? ACR_BINARY_WINDOWS : ACR_BINARY;
    }

    /**
     * Determines the CLI home path where files will be installed.
     * Uses ACR_HOME if set and valid, otherwise uses ACR_INSTALL_PATH.
     */
    private Path determineCliHomePath() throws IOException {
        // Default home directory, where the CLI should be installed
        final String installDir = config.getEnv(ENV_ACR_INSTALL_PATH);
        if (isBlank(installDir)) {
            throw new CliException("Environment variable " + ENV_ACR_INSTALL_PATH + " is not set.", VALIDATION_ERROR_RETURN_CODE);
        }
        log.debugf("%s=%s", ENV_ACR_INSTALL_PATH, installDir);
        final Path installDirPath = Paths.get(installDir).normalize().toAbsolutePath();

        // Location of the CLI home directory, set only if the CLI is already installed
        final String cliHome = config.getEnv(ENV_ACR_HOME);
        log.debugf("%s=%s", ENV_ACR_HOME, cliHome);
        Path cliHomePath = null;

        if (!isBlank(cliHome)) {
            cliHomePath = Path.of(cliHome).normalize().toAbsolutePath();
            if (!Files.exists(cliHomePath)) {
                log.debugf("ACR_HOME path does not exist, falling back to install dir: %s", cliHomePath);
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
     * Gets the appropriate shell configuration file name for the current OS.
     *
     * @return ZSHRC for macOS, BASHRC for Linux
     */
    public static String getShellConfigFile() {
        return PlatformUtils.isMacOS() ? ZSHRC : BASHRC;
    }

    /**
     * Copies all necessary files to the CLI home directory.
     * The distribution ZIP already contains the correct OS-specific acr_env file,
     * so no OS detection is needed for file selection.
     */
    // Files overwritten or modified in place by copyDistributionFiles; all are backed up so a
    // failed install can be rolled back to the previous working installation. Windows uses the
    // ".cmd" launcher and the ".exe" binary, and has no shell environment file to source.
    private static String[] installedFiles() {
        if (PlatformUtils.isWindows()) {
            return new String[] {ACR_SCRIPT_WINDOWS, ACR_BINARY_WINDOWS, README, COMPLETIONS, CONFIG_JSON};
        }
        return new String[] {ACR_SCRIPT, ACR_BINARY, README, COMPLETIONS, ACR_ENV, CONFIG_JSON};
    }

    private void copyFiles(final Path currentPath, final Path cliHomePath) throws IOException {
        // Back up every installed file that will be overwritten so a failed copy can be rolled
        // back, keeping the previous working installation until the new one is fully in place.
        final Map<Path, Path> backups = backupExisting(cliHomePath, installedFiles());
        try {
            copyDistributionFiles(currentPath, cliHomePath);
            deleteBackups(backups);
        } catch (IOException | RuntimeException e) {
            restoreBackups(backups, e);
            throw e;
        }
    }

    private void copyDistributionFiles(final Path currentPath, final Path cliHomePath) throws IOException {
        // Copy common files
        final String scriptName = getAcrScriptName();
        final String binaryName = getAcrBinaryName();
        Files.copy(currentPath.resolve(scriptName), cliHomePath.resolve(scriptName), REPLACE_EXISTING);
        Files.copy(currentPath.resolve(binaryName), cliHomePath.resolve(binaryName), REPLACE_EXISTING);
        // Ensure the binary is executable. Windows has no executable bit to set — a file is
        // executable there by virtue of its ".exe" or ".cmd" extension — so the call carries no
        // meaning, and neither does failing the install on its result.
        if (!PlatformUtils.isWindows() && !cliHomePath.resolve(binaryName).toFile().setExecutable(true, false)) {
            throw new CliException("Failed to set executable permission on " + cliHomePath.resolve(binaryName),
                    VALIDATION_ERROR_RETURN_CODE);
        }
        Files.copy(currentPath.resolve(README), cliHomePath.resolve(README), REPLACE_EXISTING);

        // Copy completions and shell environment file
        Files.copy(currentPath.resolve(COMPLETIONS), cliHomePath.resolve(COMPLETIONS), REPLACE_EXISTING);
        if (!PlatformUtils.isWindows()) {
            // The shell environment file is sourced by bash and zsh; neither cmd nor PowerShell
            // has an equivalent, so Windows persists the environment instead (see run()).
            Files.copy(currentPath.resolve(ACR_ENV), cliHomePath.resolve(ACR_ENV), REPLACE_EXISTING);
            FileUtils.replaceInFile(cliHomePath.resolve(ACR_ENV), ACR_HOME_PLACEHOLDER, cliHomePath.toAbsolutePath().toString());
        }

        // Copy config.json only if it doesn't exist (preserve user settings)
        var targetConfig = cliHomePath.resolve(CONFIG_JSON);
        if (!Files.exists(targetConfig)) {
            Files.copy(currentPath.resolve(CONFIG_JSON), targetConfig);
        } else {
            var existing = Mapper.MAPPER.readValue(targetConfig.toFile(), ConfigModel.class);
            log.debugf("Existing installation version: %d, current: %d",
                    existing.getInstallationVersion(), ConfigModel.CURRENT_INSTALLATION_VERSION);
            if (existing.getInstallationVersion() > ConfigModel.CURRENT_INSTALLATION_VERSION) {
                throw new CliException(
                        "Existing installation (version %d) is newer than this CLI supports (version %d). Cannot downgrade."
                                .formatted(existing.getInstallationVersion(), ConfigModel.CURRENT_INSTALLATION_VERSION),
                        VALIDATION_ERROR_RETURN_CODE);
            }
            if (existing.getInstallationVersion() < ConfigModel.CURRENT_INSTALLATION_VERSION) {
                existing.setInstallationVersion(ConfigModel.CURRENT_INSTALLATION_VERSION);
                Mapper.MAPPER.writeValue(targetConfig.toFile(), existing);
                log.debugf("Updated installation version to %d", ConfigModel.CURRENT_INSTALLATION_VERSION);
            }
        }
    }

    /**
     * Copies each existing file to a sibling {@code .bak} file so it can be restored if the
     * installation fails partway through. Files that do not yet exist (fresh install) are skipped.
     *
     * <p>On Windows the launcher and the binary are renamed instead of copied. {@code acr update}
     * runs the installer while the installed {@code acr_runner.exe} that spawned it is still
     * running, and Windows locks the image of a running executable, so copying over it fails with
     * an {@code AccessDeniedException}. Renaming a running executable is permitted, so taking
     * those two backups by renaming both frees the name for the incoming file and preserves the
     * previous version, which is what the copy achieves for every other file.
     *
     * @return a map of original path to its backup path
     */
    private Map<Path, Path> backupExisting(final Path cliHomePath, final String... fileNames) throws IOException {
        final Map<Path, Path> backups = new LinkedHashMap<>();
        for (final String fileName : fileNames) {
            final Path target = cliHomePath.resolve(fileName);
            if (Files.exists(target)) {
                final Path backup = cliHomePath.resolve(fileName + BACKUP_SUFFIX);
                if (isLockedWhileRunning(fileName)) {
                    Files.move(target, backup, REPLACE_EXISTING);
                } else {
                    Files.copy(target, backup, REPLACE_EXISTING, COPY_ATTRIBUTES);
                }
                backups.put(target, backup);
            }
        }
        return backups;
    }

    /**
     * Whether the given installed file may be held open by the process performing the install,
     * and so has to be renamed out of the way rather than overwritten.
     */
    private static boolean isLockedWhileRunning(final String fileName) {
        return PlatformUtils.isWindows()
                && (ACR_BINARY_WINDOWS.equals(fileName) || ACR_SCRIPT_WINDOWS.equals(fileName));
    }

    /**
     * Restores the previous installation from the backups and removes the backup files, so the
     * user is left with the working version they had before the failed install. A restore that
     * itself fails is attached to the original failure as a suppressed exception, since it may
     * leave a partially-overwritten file behind that the caller needs to know about.
     */
    private void restoreBackups(final Map<Path, Path> backups, final Throwable primary) {
        backups.forEach((original, backup) -> {
            try {
                // Copying back also covers the files Windows renamed away: whatever now occupies
                // the original name was written by this failed install and is not running, so it
                // can be overwritten.
                Files.copy(backup, original, REPLACE_EXISTING, COPY_ATTRIBUTES);
                Files.deleteIfExists(backup);
                log.debugf("Rolled back %s from backup", original);
            } catch (IOException restoreError) {
                log.errorf(restoreError, "Failed to roll back %s from backup %s", original, backup);
                primary.addSuppressed(restoreError);
            }
        });
    }

    /**
     * Removes the backup files after a successful installation.
     */
    private void deleteBackups(final Map<Path, Path> backups) {
        backups.values().forEach(backup -> {
            try {
                Files.deleteIfExists(backup);
            } catch (IOException e) {
                if (PlatformUtils.isWindows()) {
                    // Expected during "acr update": the backup is the renamed executable of the
                    // process that started this install, and Windows keeps it locked until that
                    // process exits. The next install replaces it, so leaving it behind is safe.
                    log.debugf("Backup file %s is still in use and will be replaced by the next install", backup);
                } else {
                    log.warnf("Could not delete backup file %s: %s", backup, e.getMessage());
                }
            }
        });
    }

    /**
     * Gets the user's home directory path. Windows sets USERPROFILE rather than HOME, though
     * Git Bash and similar environments set both, so HOME is still preferred when it is present.
     */
    private Path getUserHomePath() {
        String userHome = config.getEnv(ENV_HOME);
        if (isBlank(userHome) && PlatformUtils.isWindows()) {
            userHome = config.getEnv(ENV_USERPROFILE);
        }
        if (isBlank(userHome)) {
            throw new CliException(PlatformUtils.isWindows()
                    ? "Neither " + ENV_HOME + " nor " + ENV_USERPROFILE + " environment variable is set."
                    : ENV_HOME + " environment variable is not set.",
                    VALIDATION_ERROR_RETURN_CODE);
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
     * Copies the launcher script into the bin directory on Windows, where creating a symbolic
     * link requires Developer Mode or an elevated prompt. The copy is self-locating: it finds the
     * binary through ACR_HOME, which {@link #updateUserEnvironment} persists.
     */
    private void copyLauncherToBinDirectory(final Path binPath, final Path cliHomePath) throws IOException {
        Files.copy(cliHomePath.resolve(ACR_SCRIPT_WINDOWS), binPath.resolve(ACR_SCRIPT_WINDOWS), REPLACE_EXISTING);
        log.debugf("Copied %s to: %s", ACR_SCRIPT_WINDOWS, binPath);
    }

    /**
     * Persists ACR_HOME and the bin directory on the user's Path, so that {@code acr} resolves in
     * shells started after the install. Replaces the shell configuration file update used on the
     * other platforms.
     *
     * <p>The Path entry is added only when it is not already present, so repeated installs do not
     * grow the variable. The read-modify-write of Path assumes a single installer running at a
     * time, which matches how the CLI is installed; two concurrent installs could still lose one
     * of the two edits, and that is out of scope here.
     */
    private void updateUserEnvironment(final Path cliHomePath, final Path binPath) {
        userEnvironment.setUserVariable(ENV_ACR_HOME, cliHomePath.toString());

        final String binDirectory = binPath.toString();
        final String currentPath = userEnvironment.getUserVariable(ENV_PATH);
        if (currentPath == null) {
            userEnvironment.setUserVariable(ENV_PATH, binDirectory);
            return;
        }
        final boolean alreadyPresent = Stream.of(currentPath.split(PATH_SEPARATOR, -1))
                .map(String::trim)
                // Windows paths are case-insensitive, and a trailing separator does not change
                // the directory a Path entry refers to.
                .anyMatch(entry -> trimTrailingSeparators(entry).equalsIgnoreCase(trimTrailingSeparators(binDirectory)));
        if (alreadyPresent) {
            log.debugf("%s is already on the user Path", binDirectory);
            return;
        }
        userEnvironment.setUserVariable(ENV_PATH, binDirectory + PATH_SEPARATOR + currentPath);
    }

    private static String trimTrailingSeparators(final String path) {
        String result = path;
        while (result.length() > 1 && (result.endsWith("\\") || result.endsWith("/"))) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
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
                Files.writeString(shellConfigPath, "\n" + sourceCmd + CLI_MARKER_COMMENT + "\n", StandardOpenOption.APPEND);
                log.debugf("Updated %s at: %s", shellConfigPath.getFileName(), shellConfigPath);
            }
        } else {
            log.warnf("Could not update '%s'. File does not exist at: %s", shellConfigPath.getFileName(), shellConfigPath);
        }

        return shellConfigPath;
    }
}
