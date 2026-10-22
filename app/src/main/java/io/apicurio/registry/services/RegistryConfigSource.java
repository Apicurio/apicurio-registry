package io.apicurio.registry.services;

import io.quarkus.runtime.LaunchMode;
import org.eclipse.microprofile.config.spi.ConfigSource;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * ConfigSource that turns env vars into plain properties.
 * <p>
 */
public class RegistryConfigSource implements ConfigSource {
    // Computed once, eagerly, from environment variables - which are fixed for the lifetime of
    // the JVM - so this can be a plain final field instead of a lazily-initialized one guarded by
    // a lock. The previous implementation synchronized on `this` inside getProperties(), but
    // every single config lookup that reaches this ConfigSource (getValue(), getPropertyNames())
    // goes through getProperties(), including code paths that re-read config dynamically
    // per-request rather than once at injection time - under concurrent load this made the lock
    // a global serialization point, confirmed via thread dumps showing multiple threads BLOCKED
    // waiting on this monitor.
    private final Map<String, String> properties;

    public RegistryConfigSource() {
        this(computeProperties());
    }

    // Package-private, for tests to supply fixed properties directly instead of relying on
    // System.getenv() (which real env vars aren't practical to control from a unit test).
    RegistryConfigSource(Map<String, String> properties) {
        this.properties = properties;
    }

    private static Map<String, String> computeProperties() {
        Map<String, String> properties = new HashMap<>();
        String prefix = System.getenv("REGISTRY_PROPERTIES_PREFIX");
        if (prefix != null) {
            String profile = LaunchMode.current().getProfileKey();
            String profilePrefix = "%" + profile + ".";
            Map<String, String> envMap = System.getenv();
            for (Map.Entry<String, String> entry : envMap.entrySet()) {
                String key = entry.getKey();
                if (key.startsWith(prefix)) {
                    String newKey = profilePrefix + key.replace("_", ".").toLowerCase();
                    properties.put(newKey, entry.getValue());
                }
            }
        }
        return properties;
    }

    @Override
    public Map<String, String> getProperties() {
        return properties;
    }

    @Override
    public Set<String> getPropertyNames() {
        return new HashSet<>(getProperties().keySet());
    }

    @Override
    public String getValue(String key) {
        return getProperties().get(key);
    }

    @Override
    public String getName() {
        return "Registry properties";
    }
}
