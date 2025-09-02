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

        // Location of the CLI home directory, set only if the CLI is already installed
        var home = System.getenv("ACR_HOME");
        log.debug("ACR_HOME={}", home);
        var homePath = Path.of(home).normalize().toAbsolutePath();

        // Default home directory, where the CLI should be installed
        var installDir = System.getenv("ACR_INSTALL_PATH");
        log.debug("ACR_INSTALL_PATH={}", installDir);
        var installDirPath = Paths.get(installDir).normalize().toAbsolutePath();

        // Check if the home directory exists, and if not, use the install path.
        if (isBlank(home) || !Files.exists(homePath)) {
            if (isBlank(installDir)) {
                throw new CliException("Installation path is not set. Please set the ACR_INSTALL_PATH environment variable " +
                        "to the desired installation directory.", VALIDATION_ERROR_RETURN_CODE);
            }
            Files.createDirectories(installDirPath);
            log.debug("Created directory: {}", installDirPath);
            homePath = installDirPath;
        }

        // Copy files:
        Files.copy(currentPath.resolve("acr"), homePath.resolve("acr"), REPLACE_EXISTING);
        Files.copy(currentPath.resolve("acr.jar"), homePath.resolve("acr.jar"), REPLACE_EXISTING);
        Files.copy(currentPath.resolve("acr_bash_completions"), homePath.resolve("acr_bash_completions"), REPLACE_EXISTING);
        Files.copy(currentPath.resolve("acr_bash_env"), homePath.resolve("acr_bash_env"), REPLACE_EXISTING);
        Files.copy(currentPath.resolve("README.md"), homePath.resolve("README.md"), REPLACE_EXISTING);
        // `config.json` is intentionally not copied if it exists to preserve user settings
        if (!Files.exists(homePath.resolve("config.json"))) {
            Files.copy(currentPath.resolve("config.json"), homePath.resolve("config.json"));
        }

        // Set ACR_HOME
        FileUtils.replaceInFile(homePath.resolve("acr_bash_env"), "{{ACR_HOME}}", homePath.toAbsolutePath().toString());

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

        FileUtils.createLink(binPath.resolve("acr"), homePath.resolve("acr"));
        FileUtils.createLink(binPath.resolve("acr_bash_env"), homePath.resolve("acr_bash_env"));

        // Update .bashrc
        // TODO: `.bashrc` does not exist on MacOS by default, support `.zshrc`. Same for the completions.
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
