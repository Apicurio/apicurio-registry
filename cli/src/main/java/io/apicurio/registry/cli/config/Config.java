package io.apicurio.registry.cli.config;

import io.apicurio.registry.cli.InstallCommand;
import io.apicurio.registry.cli.common.CliException;
import io.apicurio.registry.cli.services.CliVersion;
import io.apicurio.registry.cli.utils.Mapper;
import io.apicurio.registry.cli.utils.Output;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.Setter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import static io.apicurio.registry.cli.common.CliException.APPLICATION_ERROR_RETURN_CODE;
import static io.apicurio.registry.cli.common.CliException.VALIDATION_ERROR_RETURN_CODE;
import static io.apicurio.registry.cli.utils.Mapper.copy;
import static io.apicurio.registry.cli.utils.Utils.isBlank;
import static java.lang.System.err;
import static java.lang.System.out;

@ApplicationScoped
public class Config {

    private static final String CONTEXT_AUTO_UPDATE_PROPERTY = "context.auto-update";

    /**
     * Static reference for use by {@link AcrHomeConfigSource}, which is loaded via SPI
     * before CDI is available. Commands should use {@code @Inject Config} instead.
     */
    static Config instance;

    private ConfigModel cachedConfig;

    @Getter
    @Setter
    private Output stdOut = out::print;

    @Getter
    @Setter
    private Output stdErr = err::print;

    @Setter
    private Path acrCurrentHomePath;

    @ConfigProperty(name = "version")
    String cliVersion;

    private final Map<String, String> envOverrides = new HashMap<>();

    void onStart(@Observes StartupEvent ev) {
        instance = this;
    }

    public CliVersion getCliVersion() {
        var version = CliVersion.parse(cliVersion);
        if (version == null || !version.isParsed()) {
            throw new CliException("Could not parse CLI version: " + cliVersion, APPLICATION_ERROR_RETURN_CODE);
        }
        return version;
    }

    /**
     * Gets an environment variable value, checking overrides first.
     * This allows tests to inject environment variables without modifying the system environment.
     *
     * @param name the environment variable name
     * @return the environment variable value (from override or system), or null if not set
     */
    public String getEnv(final String name) {
        if (envOverrides.containsKey(name)) {
            return envOverrides.get(name);
        }
        return System.getenv(name);
    }

    /**
     * Sets an environment variable override for testing purposes.
     * The override takes precedence over the actual system environment variable.
     *
     * @param name  the environment variable name
     * @param value the value to return (null to simulate unset variable)
     */
    public void setEnvOverride(final String name, final String value) {
        envOverrides.put(name, value);
    }

    /**
     * Clears all environment variable overrides.
     * Should be called in test teardown to avoid test interference.
     */
    public void clearEnvOverrides() {
        envOverrides.clear();
    }

    public Path getAcrHomePath() {
        var home = getEnv("ACR_HOME");
        if (isBlank(home)) {
            throw new CliException("ACR_HOME is not set. Please run the 'install' command first.", APPLICATION_ERROR_RETURN_CODE);
        }
        var homePath = Path.of(home).normalize().toAbsolutePath();
        if (!java.nio.file.Files.exists(homePath)) {
            throw new CliException("ACR_HOME directory does not exist: " + homePath + ". Please run the 'install' command first.",
                    APPLICATION_ERROR_RETURN_CODE);
        }
        return homePath;
    }

    public Path getAcrCurrentHomePath() {
        if (acrCurrentHomePath != null) {
            return acrCurrentHomePath;
        } else {
            var acrCurrentHome = System.getenv("ACR_CURRENT_HOME");
            if (isBlank(acrCurrentHome)) {
                throw new CliException("ACR_CURRENT_HOME environment variable is not set.", APPLICATION_ERROR_RETURN_CODE);
            }
            acrCurrentHomePath = Path.of(acrCurrentHome).normalize().toAbsolutePath();
        }
        return acrCurrentHomePath;
    }

    Path getConfigFilePath() {
        return getAcrCurrentHomePath().resolve("config.json");
    }

    public ConfigModel read() {
        if (cachedConfig == null) {
            var configPath = getConfigFilePath();
            try {
                cachedConfig = Mapper.MAPPER.readValue(configPath.toFile(), ConfigModel.class);
            } catch (IOException ex) {
                throw new CliException("Could not read config file '%s'.".formatted(configPath), ex, APPLICATION_ERROR_RETURN_CODE);
            }
        }
        return copy(cachedConfig);
    }

    public void write(ConfigModel config) {
        var configPath = getConfigFilePath();
        ensureWritable(configPath);
        try {
            Mapper.MAPPER.writeValue(configPath.toFile(), config);
            cachedConfig = null;
        } catch (IOException ex) {
            throw new CliException("Could not write config file '%s'.".formatted(configPath), ex, APPLICATION_ERROR_RETURN_CODE);
        }
    }

    /**
     * Verifies that the CLI config file can be written, failing with a clear, actionable error before
     * the caller does any work or reports success. A global installation places {@code config.json} in
     * a shared, root-owned location, so a non-root user cannot write it; without this check the user
     * would see a success message immediately followed by a permission-denied failure.
     *
     * <p>The check is best-effort: {@link Files#isWritable} reflects the state at call time, so the
     * subsequent write still guards against a late failure. Per-user installs keep the config in a
     * writable location, so this passes silently and their behavior is unchanged.
     *
     * @throws CliException with {@link CliException#VALIDATION_ERROR_RETURN_CODE} when the config file
     *         is not writable, explaining the global-install limitation when that is the cause.
     */
    private void ensureWritable(Path configPath) {
        if (isWritable(configPath)) {
            return;
        }
        var message = isGlobalInstall()
                ? "Cannot write to the CLI config file '%s'. This CLI was installed globally, and per-user configuration for global installs is not yet supported.".formatted(configPath)
                : "Cannot write to the CLI config file '%s'. Please check that you have permission to write it.".formatted(configPath);
        throw new CliException(message, VALIDATION_ERROR_RETURN_CODE);
    }

    private static boolean isWritable(Path file) {
        if (Files.exists(file)) {
            return Files.isWritable(file);
        }
        // The config file does not exist yet: it can be created only if the nearest existing ancestor
        // directory is writable.
        var probe = file.getParent();
        while (probe != null && !Files.exists(probe)) {
            probe = probe.getParent();
        }
        return probe != null && Files.isWritable(probe);
    }

    /**
     * Reports whether this CLI was installed system-wide (global). The scope is recorded in
     * {@code config.json} at install time (see {@code InstallCommand}); a global install shares one
     * root-owned config across all users. Best-effort: any failure to read the marker is treated as a
     * per-user installation.
     */
    public boolean isGlobalInstall() {
        try {
            return Boolean.parseBoolean(read().getConfig().get(InstallCommand.CONFIG_KEY_GLOBAL_INSTALL));
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /**
     * Whether the {@code context.auto-update} property is enabled.
     */
    public boolean isAutoUpdateEnabled() {
        return Boolean.parseBoolean(read().getConfig().get(CONTEXT_AUTO_UPDATE_PROPERTY));
    }

    /**
     * Applies {@code mutator} to the current context and persists the result. No-op if there is no
     * current context set, or if the current context does not exist.
     */
    public void updateCurrentContext(final Consumer<ConfigModel.Context> mutator) {
        final var model = read();
        final var contextName = model.getCurrentContext();
        if (isBlank(contextName)) {
            return;
        }
        final var context = model.getContext().get(contextName);
        if (context == null) {
            return;
        }
        mutator.accept(context);
        write(model);
    }

    /**
     * Resets cached state. Should be called between tests to avoid interference.
     */
    public void reset() {
        cachedConfig = null;
        stdOut = out::print;
        stdErr = err::print;
        acrCurrentHomePath = null;
        envOverrides.clear();
    }
}
