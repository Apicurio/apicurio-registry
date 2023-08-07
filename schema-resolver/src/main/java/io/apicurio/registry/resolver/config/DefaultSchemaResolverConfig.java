/*
 * Copyright 2021 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.resolver.config;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static io.apicurio.registry.resolver.SchemaResolverConfig.*;
import static java.util.Map.entry;


/**
 * @author Fabian Martinez
 * @author Jakub Senko <em>m@jsenko.net</em>
 */
public class DefaultSchemaResolverConfig {

    private static final Map<String, Object> DEFAULTS = Map.ofEntries(
            entry(ARTIFACT_RESOLVER_STRATEGY, ARTIFACT_RESOLVER_STRATEGY_DEFAULT),
            entry(AUTO_REGISTER_ARTIFACT, AUTO_REGISTER_ARTIFACT_DEFAULT),
            entry(AUTO_REGISTER_ARTIFACT_IF_EXISTS, AUTO_REGISTER_ARTIFACT_IF_EXISTS_DEFAULT),
            entry(FIND_LATEST_ARTIFACT, FIND_LATEST_ARTIFACT_DEFAULT),
            entry(CHECK_PERIOD_MS, CHECK_PERIOD_MS_DEFAULT),
            entry(RETRY_COUNT, RETRY_COUNT_DEFAULT),
            entry(RETRY_BACKOFF_MS, RETRY_BACKOFF_MS_DEFAULT),
            entry(DEREFERENCE_SCHEMA, DEREFERENCE_SCHEMA_DEFAULT)
    );

    private Map<String, ?> originals;

    public DefaultSchemaResolverConfig(Map<String, ?> originals) {
        this.originals = originals;
    }

    public String getRegistryUrl() {
        return getString(REGISTRY_URL);
    }

    public String getTokenEndpoint() {
        return getString(AUTH_TOKEN_ENDPOINT);
    }

    public String getAuthServiceUrl() {
        return getString(AUTH_SERVICE_URL);
    }

    public String getAuthRealm() {
        return getString(AUTH_REALM);
    }

    public String getAuthClientId() {
        return getString(AUTH_CLIENT_ID);
    }

    public String getAuthClientSecret() {
        return getString(AUTH_CLIENT_SECRET);
    }

    public String getAuthClientScope() {
        return getString(AUTH_CLIENT_SCOPE);
    }

    public String getAuthUsername() {
        return getString(AUTH_USERNAME);
    }

    public String getAuthPassword() {
        return getString(AUTH_PASSWORD);
    }

    public Object getArtifactResolverStrategy() {
        return getObject(ARTIFACT_RESOLVER_STRATEGY);
    }

    public boolean autoRegisterArtifact() {
        // Should be non-null, a default value is defined
        return getBoolean(AUTO_REGISTER_ARTIFACT);
    }

    public String autoRegisterArtifactIfExists() {
        return getStringOneOf(AUTO_REGISTER_ARTIFACT_IF_EXISTS, "FAIL", "UPDATE", "RETURN", "RETURN_OR_UPDATE");
    }

    public boolean findLatest() {
        // Should be non-null, a default value is defined
        return getBoolean(FIND_LATEST_ARTIFACT);
    }

    public Duration getCheckPeriod() {
        return getDurationNonNegativeMillis(CHECK_PERIOD_MS);
    }

    public long getRetryCount() {
        return getLongNonNegative(RETRY_COUNT);
    }

    public Duration getRetryBackoff() {
        return getDurationNonNegativeMillis(RETRY_BACKOFF_MS);
    }

    public String getExplicitArtifactGroupId() {
        return getString(EXPLICIT_ARTIFACT_GROUP_ID);
    }

    public String getExplicitArtifactId() {
        return getString(EXPLICIT_ARTIFACT_ID);
    }

    public String getExplicitArtifactVersion() {
        return getString(EXPLICIT_ARTIFACT_VERSION);
    }

    public Map<String, Object> originals() {
        return new HashMap<>(originals);
    }

    Object getObject(String key) {
        if (key == null) {
            throw new NullPointerException("Configuration property key is null.");
        }
        if (!originals.containsKey(key) && DEFAULTS.containsKey(key)) {
            return DEFAULTS.get(key);
        }
        return originals.get(key);
    }

    public boolean dereference() {
        return getBooleanOrFalse(DEREFERENCE_SCHEMA);
    }

    private Duration getDurationNonNegativeMillis(String key) {
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

    private long getLongNonNegative(String key) {
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


    private String getString(String key) {
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

    private String getStringOneOf(String key, String... possibilities) {
        String result = getString(key);
        if (!Arrays.asList(possibilities).contains(result)) {
            reportError(key, "one of " + Arrays.toString(possibilities), result);
        }
        return result;
    }

    private Boolean getBooleanOrFalse(String key) {
        var val = getBoolean(key);
        return val != null && val;
    }

    private Boolean getBoolean(String key) {
        Object value = getObject(key);
        if (value == null) {
            return null;
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

    private void reportError(String key, String expectedText, Object value) {
        throw new IllegalArgumentException("Invalid configuration property value for '" + key + "'. " +
                "Expected " + expectedText + ", but got a '" + value + "'.");
    }
}
