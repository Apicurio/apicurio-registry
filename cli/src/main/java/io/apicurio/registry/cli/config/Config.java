package io.apicurio.registry.cli.config;

import io.apicurio.registry.cli.common.CliException;
import io.apicurio.registry.cli.utils.Mapper;
import io.apicurio.registry.cli.utils.Output;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.nio.file.Path;

import static io.apicurio.registry.cli.common.CliException.APPLICATION_ERROR_RETURN_CODE;
import static io.apicurio.registry.cli.utils.Mapper.copy;
import static io.apicurio.registry.cli.utils.Utils.isBlank;
import static java.lang.System.err;
import static java.lang.System.out;

public final class Config {

    private static Config instance;

    public static synchronized Config getInstance() {
        if (instance == null) {
            instance = new Config();
        }
        return instance;
    }

    private ConfigModel cachedConfig;

    @Getter
    @Setter
    private Output stdOut = out::print;

    @Getter
    @Setter
    private Output stdErr = err::print;

    @Setter
    private Path acrCurrentHomePath;

    private Config() {
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
        try {
            Mapper.MAPPER.writeValue(configPath.toFile(), config);
            cachedConfig = null;
        } catch (IOException ex) {
            throw new CliException("Could not write config file '%s'.".formatted(configPath), ex, APPLICATION_ERROR_RETURN_CODE);
        }
    }
}
