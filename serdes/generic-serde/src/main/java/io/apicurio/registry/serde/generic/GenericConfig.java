package io.apicurio.registry.serde.generic;

import java.util.Map;

public class GenericConfig {


    // NOTE: The same config map can be shared between multiple config instances
    protected Map<String, Object> rawConfig;


    public GenericConfig(Map<String, Object> rawConfig) {
        this.rawConfig = rawConfig;
    }


    public Map<String, Object> getRawConfig() {
        return rawConfig;
    }


    public boolean hasConfig(String key) {
        return rawConfig.containsKey(key);
    }


    public void setConfig(String key, Object value) {
        rawConfig.put(key, value);
    }


    public boolean getBooleanOr(String key, boolean other) {
        var res = getBoolean(key);
        return res != null ? res : other;
    }


    public Boolean getBoolean(String key) {
        var raw = rawConfig.get(key);
        if (raw == null) {
            return null;
        }
        if (raw instanceof Boolean) {
            return (Boolean) raw;
        }
        if (raw instanceof String) {
            return Boolean.parseBoolean((String) raw);
        }
        throw new RuntimeException(String.format("Could not convert '%s' to Boolean.", raw));
    }


    public String getString(String key) {
        var raw = rawConfig.get(key);
        if (raw == null) {
            return null;
        }
        if (raw instanceof String) {
            return (String) raw;
        }
        // TODO: Try toString() and issue a warning?
        throw new RuntimeException(String.format("Could not convert '%s' to String.", raw));
    }


    public <T> T getInstance(String key, Class<T> superType) {
        return Utils.newConfiguredInstance(rawConfig.get(key), superType, null, this);
    }
}
