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
        description = "Install the CLI to the user's home directory and configure bash integration"
)
public class InstallCommand extends AbstractCommand {

    private static final Logger log = LogManager.getRootLogger();

    @Override
    public void run(OutputBuffer output) throws IOException {
        // Location of the directory where the current CLI .jar is running from
        var currentPath = Config.getInstance().getAcrCurrentHomePath();
        log.debug("Current home path: {}", currentPath);

        // Default home directory, where the CLI should be installed
        var installDir = System.getenv("ACR_INSTALL_PATH");
        if (isBlank(installDir)) {
            throw new CliException("Environment variable ACR_INSTALL_PATH is not set.", VALIDATION_ERROR_RETURN_CODE);
        }
        log.debug("ACR_INSTALL_PATH={}", installDir);
        var installDirPath = Paths.get(installDir).normalize().toAbsolutePath();

        // Location of the CLI home directory, set only if the CLI is already installed
        var cliHome = System.getenv("ACR_HOME");
        log.debug("ACR_HOME={}", cliHome);
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

        // Copy files:
        Files.copy(currentPath.resolve("acr"), cliHomePath.resolve("acr"), REPLACE_EXISTING);
        Files.copy(currentPath.resolve("acr.jar"), cliHomePath.resolve("acr.jar"), REPLACE_EXISTING);
        Files.copy(currentPath.resolve("acr_bash_completions"), cliHomePath.resolve("acr_bash_completions"), REPLACE_EXISTING);
        Files.copy(currentPath.resolve("acr_bash_env"), cliHomePath.resolve("acr_bash_env"), REPLACE_EXISTING);
        Files.copy(currentPath.resolve("README.md"), cliHomePath.resolve("README.md"), REPLACE_EXISTING);
        // `config.json` is intentionally not copied if it exists to preserve user settings
        if (!Files.exists(cliHomePath.resolve("config.json"))) {
            Files.copy(currentPath.resolve("config.json"), cliHomePath.resolve("config.json"));
        }

        // Set ACR_HOME
        FileUtils.replaceInFile(cliHomePath.resolve("acr_bash_env"), "{{ACR_HOME}}", cliHomePath.toAbsolutePath().toString());

        var userHome = System.getenv("HOME");
        if (isBlank(userHome)) {
            throw new CliException("HOME environment variable is not set.", VALIDATION_ERROR_RETURN_CODE);
        }
        var userHomePath = Path.of(userHome).normalize().toAbsolutePath();

        var binPath = userHomePath.resolve("bin");
        if (!Files.exists(binPath)) {
            Files.createDirectories(binPath);
            output.writeStdOutLine("Created bin directory at '" + binPath + "'. " +
                    "Make sure your system is configured to look for executable files in this directory.");
        }

        FileUtils.createLink(binPath.resolve("acr"), cliHomePath.resolve("acr"));
        FileUtils.createLink(binPath.resolve("acr_bash_env"), cliHomePath.resolve("acr_bash_env"));

        // Update .bashrc
        // TODO: `.bashrc` does not exist on macOS by default, support `.zshrc`. Same for the completions.
        var bashrcPath = userHomePath.resolve(".bashrc");
        if (Files.exists(bashrcPath)) {
            // Append "source (binPath)/acr_bash_env" to .bashrc if not already present
            var sourceCmd = "source " + binPath.resolve("acr_bash_env");
            if (!FileUtils.findInFile(bashrcPath, sourceCmd)) {
                try {
                    Files.writeString(bashrcPath, "\n" + sourceCmd + " # Apicurio Registry CLI\n", StandardOpenOption.APPEND);
                    log.debug("Updated .bashrc at: {}", bashrcPath);

                } catch (IOException e) {
                    log.error("Failed to update .bashrc at: {}", bashrcPath, e);
                    throw new RuntimeException(e);
                }
            }
        } else {
            log.warn("Could not update '.bashrc'. File does not exist at: {}", bashrcPath);
        }
        output.writeStdOutLine("Installation complete. Please restart your terminal or run `source " + bashrcPath + "`.");
    }
}
