package io.apicurio.registry.utils;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.inject.spi.InjectionPoint;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * @author Ales Justin
 */
public class PropertiesUtil {
    private static final Logger log = LoggerFactory.getLogger(PropertiesUtil.class);
    // explicit debug, since properties can have unresolved env vars
    private static final boolean debug = Boolean.getBoolean("registry.debug");

    /**
     * Filter/strip prefixes from configuration properties.
     *
     * @param ip the injection point annotated with {@link RegistryProperties}
     * @return filtered/stripped properties
     */
    public static Properties properties(InjectionPoint ip) {
        RegistryProperties cp = ip.getAnnotated().getAnnotation(RegistryProperties.class);
        if (cp == null) {
            throw new IllegalArgumentException(
                    ip.getMember() + " is not annotated with @RegistryProperties"
            );
        }
        String[] prefixes = Stream.of(cp.value())
                .map(pfx -> pfx.isEmpty() || pfx.endsWith(".") ? pfx : pfx + ".")
                .distinct()
                .toArray(String[]::new);
        if (prefixes.length == 0) {
            throw new IllegalArgumentException(
                    "Annotation @RegistryProperties on " + ip.getMember() +
                            " is missing non-empty 'value' attribute"
            );
        }

        Properties properties = new Properties();
        Config config = ConfigProviderResolver.instance().getConfig();

        if (debug && log.isDebugEnabled()) {
            String dump = StreamSupport
                    .stream(config.getPropertyNames().spliterator(), false)
                    .sorted()
                    .map(key -> key + "=" + config.getOptionalValue(key, String.class).orElse(""))
                    .collect(Collectors.joining("\n  ", "  ", "\n"));
            log.debug("Injecting config properties with prefixes {} into {} from the following...\n{}",
                    Arrays.toString(prefixes), ip.getMember(), dump);
        }

        // some security properties take empty value as config
        Map<String, String> defaults = new HashMap<>();
        if (cp != null) {
            String[] empties = cp.empties();
            for (String e : empties) {
                int p = e.indexOf("=");
                defaults.put(e.substring(0, p), e.substring(p + 1));
            }
        }

        // collect properties with specified prefixes in order of prefixes (later prefix overrides earlier)
        for (String prefix : prefixes) {
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
        }

        if (debug && log.isDebugEnabled()) {
            String dump = properties
                    .stringPropertyNames()
                    .stream()
                    .sorted()
                    .map(key -> key + "=" + properties.getProperty(key))
                    .collect(Collectors.joining("\n  ", "  ", "\n"));
            log.debug("... selected/prefix-stripped properties are:\n{}", dump);
        }

        return properties;
    }

}
