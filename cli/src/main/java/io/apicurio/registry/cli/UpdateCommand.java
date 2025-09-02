package io.apicurio.registry.cli;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.CliException;
import io.apicurio.registry.cli.utils.FileUtils;
import io.apicurio.registry.cli.utils.OutputBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.UUID;

import static io.apicurio.registry.cli.utils.Utils.isBlank;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

@Command(
        name = "update",
        description = "Update the CLI by installing it from the specified path"
)
public class UpdateCommand extends AbstractCommand {

    private static final Logger log = LogManager.getRootLogger();

    @Option(
            names = {"--path"},
            description = "Install the zip file specified by this path. " +
                    "Version check is not performed."
            // TODO: This might cause problems if accidentally downgrading, check versions or is a warning enough?
    )
    private Path path;

    @ParentCommand
    private Acr parent;

    @Override
    public void run(OutputBuffer output) throws Exception {
        var home = System.getenv("ACR_HOME");
        log.debug("ACR_HOME={}", home);
        var homePath = Path.of(home).normalize().toAbsolutePath();
        if (isBlank(home) || !Files.exists((homePath))) {
            throw new CliException("ACR_HOME is not set. Please run the 'install' command first.");
        }

        var targetDir = homePath.resolve(UUID.randomUUID().toString().substring(0, 8));
        try {
            Files.createDirectories(targetDir);

            Path zipFilePath;
            if (path != null) {
                var zipFile = path.getFileName();
                zipFilePath = targetDir.resolve(zipFile);
                Files.copy(path, zipFilePath, REPLACE_EXISTING);
            } else {
                // TODO: This should work, but disable it until we have released the CLI to Maven Central at least once.
                throw new UnsupportedOperationException("Automatic updates are not yet supported.");
                /*
                var latestVersion = Update.getInstance().getLatestVersion();
                // We need to coerce because the version might be non-standard, e.g. 3.1.7.redhat-00001. We'll lose the suffix.
                if (Semver.parse(ConfigProvider.getConfig().getValue("version", String.class))
                        .isGreaterThanOrEqualTo(Semver.coerce(latestVersion))) {
                    throw new CliException("You are already running the latest version.");
                }

                zipFilePath = Update.getInstance().downloadVersion(latestVersion, targetDir);
                */
            }

            FileUtils.unzip(zipFilePath, targetDir);

            // Run the acr subprocess and wait for it to finish
            Path acrPath = targetDir.resolve("acr");
            // Make the file executable
            if (!acrPath.toFile().setExecutable(true, false)) {
                throw new CliException("Failed to set executable permission on " + acrPath);
            }
            log.debug("Running subprocess: {}", acrPath);
            var cmd = new ArrayList<String>(3);
            cmd.add(acrPath.toString());
            cmd.add("install");
            if (parent.isVerbose()) {
                cmd.add("--verbose");
            }
            ProcessBuilder processBuilder = new ProcessBuilder(cmd);
            processBuilder.inheritIO(); // Pipe subprocess output to current process
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            log.debug("Subprocess exited with code: {}", exitCode);
            if (exitCode != 0) {
                throw new CliException("Update failed with exit code: " + exitCode, exitCode);
            }

            output.writeStdOutLine("Update complete.");
        } finally {
            FileUtils.deleteDirectory(targetDir);
        }
    }
}
