package io.apicurio.registry.cli.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.microprofile.config.spi.ConfigSource;

import java.util.Map;
import java.util.Set;

/**
 * Custom ConfigSource that loads properties from "$ACR_HOME/config.json".
 */
public class AcrHomeConfigSource implements ConfigSource {

    private static final Logger log = LogManager.getRootLogger();

    public AcrHomeConfigSource() {
    }

    @Override
    public Map<String, String> getProperties() {
        return Config.getInstance().read().getConfig();
    }

    @Override
    public String getValue(String propertyName) {
        return Config.getInstance().read().getConfig().get(propertyName);
    }

    @Override
    public String getName() {
        return getClass().getSimpleName() + "[" + Config.getInstance().getConfigFilePath() + "]";
    }

    @Override
    public Set<String> getPropertyNames() {
        return Config.getInstance().read().getConfig().keySet();
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
