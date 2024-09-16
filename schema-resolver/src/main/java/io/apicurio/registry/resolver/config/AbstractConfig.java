package io.apicurio.registry.resolver.config;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractConfig {

    protected Map<String, ?> originals;

    protected abstract Map<String, ?> getDefaults();

    protected Duration getDurationNonNegativeMillis(String key) {
        Object value = getObject(key);
        if (value == null) {
            reportError(key, "a non-null value", value);
        }
        long millis;

        if (value instanceof Number) {
            millis = ((Number) value).longValue();
        } else if (value instanceof String) {
            millis = Long.parseLong((String) value);
        } else if (value instanceof Duration) {
            millis = ((Duration) value).toMillis();
        } else {
            reportError(key, "a duration-like value", value);
            throw new IllegalStateException("Unreachable");
        }
        if (millis < 0) {
            reportError(key, "a non-negative duration-like value", value);
        }
        return Duration.ofMillis(millis);
    }

    protected long getLongNonNegative(String key) {
        Object value = getObject(key);
        if (value == null) {
            reportError(key, "a non-null value", value);
        }
        long result;
        if (value instanceof Number) {
            result = ((Number) value).longValue();
        } else if (value instanceof String) {
            result = Long.parseLong((String) value);
        } else {
            reportError(key, "a number-like value", value);
            throw new IllegalStateException("Unreachable");
        }
        if (result < 0) {
            reportError(key, "a non-negative number-like value", value);
        }
        return result;
    }

    protected String getString(String key) {
        Object value = getObject(key);
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return ((String) value).trim();
        } else {
            reportError(key, "a String", value.getClass().getName());
            throw new IllegalStateException("Unreachable");
        }
    }

    protected String getStringOneOf(String key, String... possibilities) {
        String result = getString(key);
        if (!Arrays.asList(possibilities).contains(result)) {
            reportError(key, "one of " + Arrays.toString(possibilities), result);
        }
        return result;
    }

    protected Boolean getBooleanOrFalse(String key) {
        var val = getBoolean(key);
        return val != null && val;
    }

    protected Boolean getBoolean(String key) {
        Object value = getObject(key);
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        } else if (value instanceof String) {
            String trimmed = ((String) value).trim();
            if (trimmed.equalsIgnoreCase("true"))
                return true;
            else if (trimmed.equalsIgnoreCase("false"))
                return false;
            else {
                reportError(key, "a boolean-like value", value);
                throw new IllegalStateException("Unreachable");
            }
        } else {
            reportError(key, "a boolean-like value", value);
            throw new IllegalStateException("Unreachable");
        }
    }

    protected Object getObject(String key) {
        if (key == null) {
            throw new NullPointerException("Configuration property key is null.");
        }
        if (!originals.containsKey(key) && getDefaults().containsKey(key)) {
            return getDefaults().get(key);
        }
        return originals.get(key);
    }

    protected Class<?> getClass(String className) {
        try {
            String originalsClassName = (String) this.originals.get(className);
            if (originalsClassName != null) {
                return this.getClass().getClassLoader().loadClass(originalsClassName);
            } else {
                return null;
            }
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public Map<String, Object> originals() {
        return new HashMap<>(originals);
    }

    private void reportError(String key, String expectedText, Object value) {
        throw new IllegalArgumentException("Invalid configuration property value for '" + key + "'. "
                + "Expected " + expectedText + ", but got a '" + value + "'.");
    }

}
