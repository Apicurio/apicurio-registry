package io.apicurio.registry.utils;

import jakarta.enterprise.inject.spi.InjectionPoint;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class PropertiesUtil {
    private static final Logger log = LoggerFactory.getLogger(PropertiesUtil.class);
    // explicit debug, since properties can have unresolved env vars
    private static final boolean debug = Boolean.getBoolean("apicurio.debug");

    /**
     * Filter/strip prefixes from configuration properties.
     *
     * @param ip the injection point annotated with {@link RegistryProperties}
     * @return filtered/stripped properties
     */
    public static Properties properties(InjectionPoint ip) {

        RegistryProperties cp = ip.getAnnotated().getAnnotation(RegistryProperties.class);
        if (cp == null) {
            throw new IllegalArgumentException(ip.getMember() + " must be annotated with @RegistryProperties.");
        }

        var prefixes = Stream.of(cp.prefixes())
                .map(pfx -> pfx.isEmpty() || pfx.endsWith(".") ? pfx : pfx + ".").distinct()
                .toList();

        if (prefixes.isEmpty()) {
            throw new IllegalArgumentException("Annotation @RegistryProperties on " + ip.getMember()
                    + " must have a non-empty 'prefixes' attribute.");
        }

        Properties properties = new Properties();
        Config config = ConfigProviderResolver.instance().getConfig();

        if (debug && log.isDebugEnabled()) {
            String dump = StreamSupport.stream(config.getPropertyNames().spliterator(), false)
                    .sorted()
                    .map(key -> key + "=" + config.getOptionalValue(key, String.class).orElse(""))
                    .collect(Collectors.joining("\n  ", "  ", "\n"));
            log.debug("Injecting config properties with prefixes {} into {} from the following...\n{}",
                    prefixes, ip.getMember(), dump);
        }

        var defaults = new HashMap<String, String>();
        for (String d : cp.defaults()) {
            int p = d.indexOf("=");
            defaults.put(d.substring(0, p), d.substring(p + 1));
        }

        var excludedList = Arrays.asList(cp.excluded());
        var excluded = new HashSet<>();
        // When the property is passed as an env. variable, dashes are interpreted as dots,
        // so we need to exclude both forms.
        excluded.addAll(excludedList);
        excluded.addAll(excludedList.stream().map(s -> s.replace('-', '.')).toList());

        // Collect properties with specified prefixes in order of prefixes (later prefix overrides earlier)
        for (String prefix : prefixes) {
            for (String key : config.getPropertyNames()) {
                if (key.startsWith(prefix)) {
                    var suffix = key.substring(prefix.length());
                    if (!suffix.isEmpty()) {
                        log.debug("Processing property '{}'", key);
                        if (!excluded.contains(suffix)) {
                            // Property can exist with a key but no value...
                            Optional<String> value = config.getOptionalValue(key, String.class);
                            if (value.isPresent()) {
                                properties.put(suffix, value.get());
                            } else {
                                String defaultValue = defaults.get(suffix);
                                if (defaultValue != null) {
                                    properties.put(suffix, defaultValue);
                                } else {
                                    log.debug("Property '{}' has no value and no default, skipping.", key);
                                }
                            }
                        } else {
                            log.debug("Property '{}' is excluded, skipping.", key);
                        }
                    }
                }
            }
        }

        if (debug && log.isDebugEnabled()) {
            String dump = properties.stringPropertyNames().stream().sorted()
                    .map(key -> key + "=" + properties.getProperty(key))
                    .collect(Collectors.joining("\n  ", "  ", "\n"));
            log.debug("... selected/prefix-stripped properties are:\n{}", dump);
        }

        return properties;
    }

}
