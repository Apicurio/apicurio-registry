package io.apicurio.registry.cli.config;

import io.apicurio.registry.cli.common.CliException;
import io.apicurio.registry.cli.services.CliVersion;
import io.apicurio.registry.cli.utils.Mapper;
import io.apicurio.registry.cli.utils.Output;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.Setter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import static io.apicurio.registry.cli.common.CliException.APPLICATION_ERROR_RETURN_CODE;
import static io.apicurio.registry.cli.utils.Utils.isBlank;
import static java.lang.System.err;
import static java.lang.System.out;

@ApplicationScoped
public class Config {

    public static final String ENV_ACR_CURRENT_HOME = "ACR_CURRENT_HOME";
    public static final String ENV_ACR_HOME = "ACR_HOME";

    // Loaded via SPI by AcrHomeConfigSource before CDI is available; commands should @Inject Config instead.
    static Config instance;

    private ConfigModel cachedConfig;
    private boolean dirty;

    @Getter
    @Setter
    private Output stdOut = out::print;

    @Getter
    @Setter
    private Output stdErr = err::print;

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
        var home = getEnv(ENV_ACR_HOME);
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
        var acrCurrentHome = getEnv(ENV_ACR_CURRENT_HOME);
        if (isBlank(acrCurrentHome)) {
            throw new CliException("ACR_CURRENT_HOME environment variable is not set.", APPLICATION_ERROR_RETURN_CODE);
        }
        return Path.of(acrCurrentHome).normalize().toAbsolutePath();
    }

    Path getConfigFilePath() {
        return getAcrCurrentHomePath().resolve("config.json");
    }

    // Returns the live cache, not a defensive copy. Mutating it directly requires calling markDirty().
    public ConfigModel read() {
        if (cachedConfig == null) {
            var configPath = getConfigFilePath();
            try {
                cachedConfig = Mapper.MAPPER.readValue(configPath.toFile(), ConfigModel.class);
            } catch (IOException ex) {
                throw new CliException("Could not read config file '%s'.".formatted(configPath), ex, APPLICATION_ERROR_RETURN_CODE);
            }
        }
        return cachedConfig;
    }

    public String getProperty(final String key) {
        return read().getConfig().get(key);
    }

    public boolean hasProperty(final String key) {
        return read().getConfig().containsKey(key);
    }

    public void setProperty(final String key, final String value) {
        read().getConfig().put(key, value);
        markDirty();
    }

    public String removeProperty(final String key) {
        var previous = read().getConfig().remove(key);
        markDirty();
        return previous;
    }

    public String getCurrentContext() {
        return read().getCurrentContext();
    }

    public ConfigModel.Context getContext(final String name) {
        return read().getContext().get(name);
    }

    public void setCurrentContext(final String name) {
        read().setCurrentContext(name);
        markDirty();
    }

    public void putContext(final String name, final ConfigModel.Context context) {
        read().getContext().put(name, context);
        markDirty();
    }

    public ConfigModel.Context removeContext(final String name) {
        var removed = read().getContext().remove(name);
        markDirty();
        return removed;
    }

    public void clearContexts() {
        read().getContext().clear();
        markDirty();
    }

    // No-op (returns false) if no context with the given name exists.
    public boolean updateContext(final String name, final Consumer<ConfigModel.Context> mutation) {
        var context = read().getContext().get(name);
        if (context == null) {
            return false;
        }
        mutation.accept(context);
        markDirty();
        return true;
    }

    public void markDirty() {
        dirty = true;
    }

    // Called once at the end of each command, from AbstractCommand.call().
    public void flush() {
        if (!dirty || cachedConfig == null) {
            return;
        }
        var configPath = getConfigFilePath();
        try {
            Mapper.MAPPER.writeValue(configPath.toFile(), cachedConfig);
            dirty = false;
        } catch (IOException ex) {
            throw new CliException("Could not write config file '%s'.".formatted(configPath), ex, APPLICATION_ERROR_RETURN_CODE);
        }
    }

    /**
     * Resets cached state. Should be called between tests to avoid interference.
     */
    public void reset() {
        cachedConfig = null;
        dirty = false;
        stdOut = out::print;
        stdErr = err::print;
        envOverrides.clear();
    }
}
