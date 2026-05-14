package io.apicurio.registry.cli.config;

import io.apicurio.registry.cli.common.CliException;
import org.eclipse.microprofile.config.spi.ConfigSource;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Custom ConfigSource that loads properties from "$ACR_HOME/config.json".
 * Uses the static bridge from {@link Config} because this class is loaded
 * via SPI before CDI is available.
 * Gracefully returns empty results when Config is not yet initialized
 * (e.g., during Quarkus build time).
 */
public class AcrHomeConfigSource implements ConfigSource {

    public AcrHomeConfigSource() {
    }

    @Override
    public Map<String, String> getProperties() {
        try {
            if (Config.instance == null) {
                return Collections.emptyMap();
            }
            return Config.instance.read().getConfig();
        } catch (CliException e) {
            return Collections.emptyMap();
        }
    }

    @Override
    public String getValue(String propertyName) {
        try {
            if (Config.instance == null) {
                return null;
            }
            return Config.instance.read().getConfig().get(propertyName);
        } catch (CliException e) {
            return null;
        }
    }

    @Override
    public String getName() {
        try {
            if (Config.instance == null) {
                return getClass().getSimpleName() + "[not configured]";
            }
            return getClass().getSimpleName() + "[" + Config.instance.getConfigFilePath() + "]";
        } catch (CliException e) {
            return getClass().getSimpleName() + "[not configured]";
        }
    }

    @Override
    public Set<String> getPropertyNames() {
        try {
            if (Config.instance == null) {
                return Collections.emptySet();
            }
            return Config.instance.read().getConfig().keySet();
        } catch (CliException e) {
            return Collections.emptySet();
        }
    }

    @Override
    public int getOrdinal() {
        /*
         * The default configuration sources are:
         *
         *  - System properties, with an ordinal value of 400.
         *  - Environment properties with an ordinal value of 300.
         *  - The `/META-INF/microprofile-config.properties` resource with an ordinal value of 100.
         */
        return 200;
    }
}
