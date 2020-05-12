package io.apicurio.registry.utils;

import io.quarkus.runtime.configuration.ProfileManager;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * @author Ales Justin
 */
public class PropertiesUtil {

    public static Properties properties(RegistryProperties kp) {
        String prefix = (kp != null ? kp.value() : "");
        Config config = ConfigProviderResolver.instance().getConfig();
        String profile = ProfileManager.getActiveProfile();
        if (profile != null && profile.length() > 0) {
            prefix = "%" + profile + "." + prefix;
        }
        Map<String, String> defaults = new HashMap<>();
        if (kp != null) {
            String[] empties = kp.empties();
            for (String e : empties) {
                int p = e.indexOf("=");
                defaults.put(e.substring(0, p), e.substring(p + 1));
            }
        }
        Properties properties = new Properties();
        for (String key : config.getPropertyNames()) {
            if (key.startsWith(prefix)) {
                // property can exist with key, but no value ...
                Optional<String> value = config.getOptionalValue(key, String.class);
                if (value.isPresent()) {
                    properties.put(key.substring(prefix.length()), value.get());
                } else if (defaults.size() > 0) {
                    String sKey = key.substring(prefix.length());
                    String defaultValue = defaults.get(sKey);
                    if (defaultValue != null) {
                        properties.put(sKey, defaultValue);
                    }
                }
            }
        }
        return properties;
    }

}
