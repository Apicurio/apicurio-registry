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

import static io.apicurio.registry.resolver.SchemaResolverConfig.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @author Fabian Martinez
 */
public class DefaultSchemaResolverConfig {

    private Map<String, ?> originals;

    public DefaultSchemaResolverConfig(Map<String, ?> originals) {
        this.originals = originals;
    }

    public String getRegistryUrl() {
        return this.getString(REGISTRY_URL);
    }

    public String getTokenEndpoint() {
        return this.getString(AUTH_TOKEN_ENDPOINT);
    }

    public String getAuthServiceUrl() {
        return this.getString(AUTH_SERVICE_URL);
    }

    public String getAuthRealm() {
        return this.getString(AUTH_REALM);
    }

    public String getAuthClientId() {
        return this.getString(AUTH_CLIENT_ID);
    }

    public String getAuthClientSecret() {
        return this.getString(AUTH_CLIENT_SECRET);
    }

    public String getAuthUsername() {
        return this.getString(AUTH_USERNAME);
    }

    public String getAuthPassword() {
        return this.getString(AUTH_PASSWORD);
    }

    public Object getArtifactResolverStrategy() {
        return this.get(ARTIFACT_RESOLVER_STRATEGY);
    }

    public boolean autoRegisterArtifact() {
        return this.getBoolean(AUTO_REGISTER_ARTIFACT);
    }

    public String autoRegisterArtifactIfExists() {
        return this.getString(AUTO_REGISTER_ARTIFACT_IF_EXISTS);
    }

    public boolean findLatest() {
        return this.getBoolean(FIND_LATEST_ARTIFACT);
    }

    public Duration getCheckPeriod() {
        return extractDurationMillis(this.get(CHECK_PERIOD_MS), CHECK_PERIOD_MS);
    }

    public long getRetryCount() {
        // No need to check for null, a default value is defined
        long result = extractLong(this.get(RETRY_COUNT), RETRY_COUNT);
        if(result < 0) {
            throw new IllegalArgumentException("Config param '" + RETRY_COUNT + "' must be non-negative. Got '" + result + "'.");
        }
        return result;
    }

    public Duration getRetryBackoff() {
        // No need to check for null, a default value is defined
        return extractDurationMillis(this.get(RETRY_BACKOFF_MS), RETRY_BACKOFF_MS);
    }

    public String getExplicitArtifactGroupId() {
        return this.getString(EXPLICIT_ARTIFACT_GROUP_ID);
    }

    public String getExplicitArtifactId() {
        return this.getString(EXPLICIT_ARTIFACT_ID);
    }

    public String getExplicitArtifactVersion() {
        Object version = this.originals.get(EXPLICIT_ARTIFACT_VERSION);
        if (version == null) {
            return null;
        }
        return version.toString();
    }

    private Duration extractDurationMillis(Object value, String configurationName) {
        Objects.requireNonNull(value);
        Objects.requireNonNull(configurationName);
        long result;
        if (value instanceof Number) {
            result = ((Number) value).longValue();
        } else if (value instanceof String) {
            result = Long.parseLong((String) value);
        } else if (value instanceof Duration) {
            result = ((Duration) value).toMillis();
        } else {
            throw new IllegalArgumentException("Config param '" + configurationName + "' type unsupported. " +
                "Expected a Number, String, or Duration. Got '" + value + "'.");
        }
        if (result < 0) {
            throw new IllegalArgumentException("Config param '" + configurationName + "' represents a duration, " +
                "which must be non-negative. Got '" + value + "'.");
        }
        return Duration.ofMillis(result);
    }

    private long extractLong(Object value, String configurationName) {
        Objects.requireNonNull(value);
        Objects.requireNonNull(configurationName);
        long result;
        if (value instanceof Number) {
            result = ((Number) value).longValue();
        } else if (value instanceof String) {
            result = Long.parseLong((String) value);
        } else {
            throw new IllegalArgumentException("Config param '" + configurationName + "' type unsupported. " +
                "Expected a Number or String. Got '" + value + "'.");
        }
        return result;
    }

    public Map<String, Object> originals() {
        return new HashMap<String, Object>(this.originals);
    }

    private Object get(String key) {
        return this.originals.get(key);
    }

    private String getString(String key) {
        return (String) get(key);
    }

    private Boolean getBoolean(String key) {
        return (Boolean) get(key);
    }
}
