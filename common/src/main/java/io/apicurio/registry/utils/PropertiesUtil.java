package io.apicurio.registry.utils;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;

import java.util.Optional;
import java.util.Properties;

/**
 * @author Ales Justin
 */
public class PropertiesUtil {

    public static Properties properties(RegistryProperties kp) {
        String prefix = (kp != null ? kp.value() : "");
        Config config = ConfigProviderResolver.instance().getConfig();
        Optional<String> po = config.getOptionalValue("quarkus.profile", String.class);
        if (po.isPresent()) {
            String profile = po.get();
            if (profile.length() > 0) {
                prefix = "%" + profile + "." + prefix;
            }
        }

        Properties properties = new Properties();
        for (String key : config.getPropertyNames()) {
            if (key.startsWith(prefix)) {
                // property can exist with key, but no value ...
                Optional<String> value = config.getOptionalValue(key, String.class);
                if (value.isPresent()) {
                    properties.put(key.substring(prefix.length()), value.get());
                }
            }
        }
        return properties;
    }

}
