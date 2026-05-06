package io.apicurio.registry.cli;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.CliException;
import io.apicurio.registry.cli.services.CliVersion;
import io.apicurio.registry.cli.services.Update;
import io.apicurio.registry.cli.utils.FileUtils;
import io.apicurio.registry.cli.utils.OutputBuffer;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static io.apicurio.registry.cli.common.CliException.VALIDATION_ERROR_RETURN_CODE;
import static io.apicurio.registry.cli.utils.Utils.isBlank;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

@Command(
        name = "update",
        description = "Update the CLI to a newer version"
)
public class UpdateCommand extends AbstractCommand {

    private static final Logger log = Logger.getLogger(UpdateCommand.class);

    @Parameters(
            index = "0",
            arity = "0..1",
            description = "Target version to update to. If not provided, the latest unambiguous version is used."
    )
    private String targetVersion;

    @Option(
            names = {"--path"},
            description = "Install from a local zip file. Version check is not performed."
    )
    private Path path;

    @Option(
            names = {"--check"},
            description = "Check for available updates without installing.",
            defaultValue = "false"
    )
    private boolean check;

    @Option(
            names = {"--postpone"},
            description = "Postpone update notifications. Default: 120 hours (5 days).",
            arity = "0..1",
            defaultValue = "-1",
            fallbackValue = "120"
    )
    private int postponeHours;

    @ParentCommand
    private Acr parent;

    @Inject
    Update update;

    @Override
    public void run(OutputBuffer output) throws Exception {
        if (postponeHours >= 0) {
            handlePostpone(output);
            return;
        }

        var currentVersion = getCurrentVersion();

        if (check) {
            handleCheck(output, currentVersion);
            return;
        }

        if (path != null) {
            handlePathInstall(output);
            return;
        }

        handleAutoUpdate(output, currentVersion);
    }

    private void handlePostpone(OutputBuffer output) {
        var configModel = config.read();
        var until = Instant.now().plusSeconds(postponeHours * 3600L);
        configModel.getConfig().put("internal.update.postponed-until", until.toString());
        config.write(configModel);
        output.writeStdOutLine("Update notifications postponed for %d hours.".formatted(postponeHours));
    }

    private void handleCheck(OutputBuffer output, CliVersion currentVersion) {
        var result = update.checkForUpdates(currentVersion);
        recordCheckTimestamp();

        if (!result.hasUpdates()) {
            output.writeStdOutLine("You are running the latest version (%s).".formatted(currentVersion));
            return;
        }

        output.writeStdOutChunk(out -> {
            result.formatMessage(out);
        });
    }

    private void handlePathInstall(OutputBuffer output) throws Exception {
        validateAcrHome();
        var homePath = getHomePath();
        var targetDir = homePath.resolve(UUID.randomUUID().toString().substring(0, 8));
        try {
            Files.createDirectories(targetDir);
            var zipFilePath = targetDir.resolve(path.getFileName());
            Files.copy(path, zipFilePath, REPLACE_EXISTING);
            runInstallFromZip(zipFilePath, targetDir, output);
        } finally {
            FileUtils.deleteDirectory(targetDir);
        }
    }

    private void handleAutoUpdate(OutputBuffer output, CliVersion currentVersion) throws Exception {
        validateAcrHome();

        var result = update.checkForUpdates(currentVersion);
        recordCheckTimestamp();

        if (!result.hasUpdates()) {
            output.writeStdOutLine("You are running the latest version (%s).".formatted(currentVersion));
            return;
        }

        String versionToDownload;
        if (targetVersion != null) {
            versionToDownload = targetVersion;
        } else {
            var unambiguous = result.unambiguousUpdate();
            if (unambiguous == null) {
                output.writeStdOutChunk(out -> {
                    out.append("Multiple update candidates available:\n");
                    result.formatMessage(out);
                    out.append("\nSpecify a version: acr update <version>\n");
                });
                return;
            }
            versionToDownload = unambiguous.toString();
            log.debugf("Auto-selected version: %s", versionToDownload);
        }

        output.writeStdOutLine("Updating to version %s...".formatted(versionToDownload));
        output.print();

        var homePath = getHomePath();
        var targetDir = homePath.resolve(UUID.randomUUID().toString().substring(0, 8));
        try {
            Files.createDirectories(targetDir);
            var zipFilePath = update.downloadVersion(versionToDownload, targetDir);
            if (zipFilePath == null) {
                throw new CliException("Failed to download version " + versionToDownload, VALIDATION_ERROR_RETURN_CODE);
            }
            runInstallFromZip(zipFilePath, targetDir, output);
        } finally {
            FileUtils.deleteDirectory(targetDir);
        }
    }

    private void runInstallFromZip(Path zipFilePath, Path targetDir, OutputBuffer output) throws Exception {
        FileUtils.unzip(zipFilePath, targetDir);

        Path acrPath = targetDir.resolve(InstallCommand.ACR_SCRIPT);
        Path acrRunnerPath = targetDir.resolve(InstallCommand.ACR_BINARY);
        for (var executable : List.of(acrPath, acrRunnerPath)) {
            if (executable.toFile().exists() && !executable.toFile().setExecutable(true, false)) {
                throw new CliException("Failed to set executable permission on " + executable);
            }
        }
        log.debugf("Running subprocess: %s", acrPath);
        var cmd = new ArrayList<String>(3);
        cmd.add(acrPath.toString());
        cmd.add("install");
        if (parent.isVerbose()) {
            cmd.add("--verbose");
        }
        ProcessBuilder processBuilder = new ProcessBuilder(cmd);
        processBuilder.inheritIO();
        Process process = processBuilder.start();
        int exitCode = process.waitFor();
        log.debugf("Subprocess exited with code: %s", exitCode);
        if (exitCode != 0) {
            throw new CliException("Update failed with exit code: " + exitCode, exitCode);
        }

        output.writeStdOutLine("Update complete.");
    }

    private CliVersion getCurrentVersion() {
        var versionStr = ConfigProvider.getConfig().getValue("version", String.class);
        var version = CliVersion.parse(versionStr);
        if (version == null) {
            throw new CliException("Could not parse current CLI version: " + versionStr, VALIDATION_ERROR_RETURN_CODE);
        }
        return version;
    }

    private void validateAcrHome() {
        var home = System.getenv("ACR_HOME");
        log.debugf("ACR_HOME=%s", home);
        if (isBlank(home)) {
            throw new CliException("ACR_HOME is not set. Please run the 'install' command first.");
        }
        if (!Files.exists(Path.of(home))) {
            throw new CliException("ACR_HOME directory does not exist: " + home + ". Please run the 'install' command first.");
        }
    }

    private Path getHomePath() {
        return Path.of(System.getenv("ACR_HOME")).normalize().toAbsolutePath();
    }

    private void recordCheckTimestamp() {
        try {
            var configModel = config.read();
            configModel.getConfig().put("internal.update.last-check", Instant.now().toString());
            config.write(configModel);
        } catch (Exception e) {
            log.debugf("Could not record update check timestamp: %s", e.getMessage());
        }
    }

}
