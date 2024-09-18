package io.apicurio.registry.resolver.config;

import java.time.Duration;
import java.util.Map;

import static io.apicurio.registry.resolver.SchemaResolverConfig.*;
import static java.util.Map.entry;

public class DefaultSchemaResolverConfig extends AbstractConfig {

    private static final Map<String, Object> DEFAULTS = Map.ofEntries(
            entry(ARTIFACT_RESOLVER_STRATEGY, ARTIFACT_RESOLVER_STRATEGY_DEFAULT),
            entry(AUTO_REGISTER_ARTIFACT, AUTO_REGISTER_ARTIFACT_DEFAULT),
            entry(AUTO_REGISTER_ARTIFACT_IF_EXISTS, AUTO_REGISTER_ARTIFACT_IF_EXISTS_DEFAULT),
            entry(CACHE_LATEST, CACHE_LATEST_DEFAULT),
            entry(FAULT_TOLERANT_REFRESH, FAULT_TOLERANT_REFRESH_DEFAULT),
            entry(FIND_LATEST_ARTIFACT, FIND_LATEST_ARTIFACT_DEFAULT),
            entry(CHECK_PERIOD_MS, CHECK_PERIOD_MS_DEFAULT), entry(RETRY_COUNT, RETRY_COUNT_DEFAULT),
            entry(RETRY_BACKOFF_MS, RETRY_BACKOFF_MS_DEFAULT),
            entry(DEREFERENCE_SCHEMA, DEREFERENCE_SCHEMA_DEFAULT),
            entry(DESERIALIZER_DEREFERENCE_SCHEMA, DESERIALIZER_DEREFERENCE_SCHEMA_DEFAULT));

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
        return getStringOneOf(AUTO_REGISTER_ARTIFACT_IF_EXISTS, "FAIL", "CREATE_VERSION",
                "FIND_OR_CREATE_VERSION");
    }

    public boolean getCacheLatest() {
        return getBoolean(CACHE_LATEST);
    }

    public boolean getFaultTolerantRefresh() {
        return getBoolean(FAULT_TOLERANT_REFRESH);
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

    public String getExplicitSchemaLocation() {
        return getString(SCHEMA_LOCATION);
    }

    public String getExplicitArtifactVersion() {
        return getString(EXPLICIT_ARTIFACT_VERSION);
    }

    public boolean serializerDereference() {
        return getBooleanOrFalse(DEREFERENCE_SCHEMA);
    }

    public boolean deserializerDereference() {
        return getBooleanOrFalse(DESERIALIZER_DEREFERENCE_SCHEMA);
    }

    @Override
    protected Map<String, ?> getDefaults() {
        return DEFAULTS;
    }
}
