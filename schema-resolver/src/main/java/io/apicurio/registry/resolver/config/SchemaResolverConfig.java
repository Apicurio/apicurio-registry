package io.apicurio.registry.resolver.config;

import io.apicurio.registry.resolver.DefaultSchemaResolver;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.data.Metadata;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.resolver.strategy.DynamicArtifactReferenceResolverStrategy;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static java.util.Map.entry;

/**
 * Contains the {@link DefaultSchemaResolver} configuration properties.
 */
public class SchemaResolverConfig extends AbstractConfig {

    public SchemaResolverConfig() {
        this.originals = DEFAULTS;
    }

    public SchemaResolverConfig(Map<String, ?> originals) {
        Map<String, Object> joint = new HashMap<>(getDefaults());
        joint.putAll(originals);
        this.originals = joint;
    }

    /**
     * Fully qualified Java classname of a class that implements {@link ArtifactReferenceResolverStrategy} and
     * is responsible for mapping between the Record being resolved and an artifactId. For example there is a
     * strategy to use the topic name as the schema's artifactId. Only used by
     * {@link SchemaResolver#resolveSchema(io.apicurio.registry.resolver.data.Record)}
     */
    public static final String ARTIFACT_RESOLVER_STRATEGY = "apicurio.registry.artifact-resolver-strategy";

    /**
     * Uses the ArtifactReference available for each record. Requires {@link Metadata#artifactReference()} to
     * be set. Note this default artifact resolver strategy differs in behavior from the classic Kafka serdes
     * ArtifactResolverStrategy
     */
    public static final String ARTIFACT_RESOLVER_STRATEGY_DEFAULT = DynamicArtifactReferenceResolverStrategy.class
            .getName();

    /**
     * Optional, boolean to indicate whether serializer classes should attempt to create an artifact in the
     * registry. Note: JsonSchema serializer does not support this feature yet.
     */
    public static final String AUTO_REGISTER_ARTIFACT = "apicurio.registry.auto-register";
    public static final boolean AUTO_REGISTER_ARTIFACT_DEFAULT = false;

    /**
     * Optional, one of "IfExists" to indicate the behavior of the client when there is a conflict creating an
     * artifact because the artifact already exists.
     */
    public static final String AUTO_REGISTER_ARTIFACT_IF_EXISTS = "apicurio.registry.auto-register.if-exists";
    public static final String AUTO_REGISTER_ARTIFACT_IF_EXISTS_DEFAULT = "FIND_OR_CREATE_VERSION";

    /**
     * Optional, boolean to indicate whether serializer classes should attempt to find the latest artifact in
     * the registry for the corresponding groupId/artifactId. GroupId and artifactId are configured either via
     * {@link ArtifactReferenceResolverStrategy} or via config properties such as
     * {@link SchemaResolverConfig#EXPLICIT_ARTIFACT_ID}.
     */
    public static final String FIND_LATEST_ARTIFACT = "apicurio.registry.find-latest";
    public static final boolean FIND_LATEST_ARTIFACT_DEFAULT = false;

    /**
     * If {@code true}, will cache schema lookups that either have `latest` or no version specified. Setting
     * this to false will effectively disable caching for schema lookups that do not specify a version.
     */
    public static final String CACHE_LATEST = "apicurio.registry.cache-latest";
    public static final boolean CACHE_LATEST_DEFAULT = true;

    /**
     * If {@code true}, will log exceptions instead of throwing them when an error occurs trying to refresh a
     * schema in the cache. This is useful for production situations where a stale schema is better than
     * completely failing schema resolution. Note that this will not impact trying of retries, as retries are
     * attempted before this flag is considered.
     */
    public static final String FAULT_TOLERANT_REFRESH = "apicurio.registry.fault-tolerant-refresh";
    public static final boolean FAULT_TOLERANT_REFRESH_DEFAULT = false;

    /**
     * Only applicable for serializers Optional, set explicitly the groupId used for querying/creating an
     * artifact. Overrides the groupId returned by the {@link ArtifactReferenceResolverStrategy}
     */
    public static final String EXPLICIT_ARTIFACT_GROUP_ID = "apicurio.registry.artifact.group-id";

    /**
     * Only applicable for serializers Optional, set explicitly the artifactId used for querying/creating an
     * artifact. Overrides the artifactId returned by the {@link ArtifactReferenceResolverStrategy}
     */
    public static final String EXPLICIT_ARTIFACT_ID = "apicurio.registry.artifact.artifact-id";

    /**
     * Only applicable for serializers Optional, set explicitly the schema location in the classpath for the
     * schema to be used for serializing the data.
     */
    public static final String SCHEMA_LOCATION = "apicurio.registry.artifact.schema.location";

    /**
     * Only applicable for serializers Optional, set explicitly the version used for querying/creating an
     * artifact. Overrides the version returned by the {@link ArtifactReferenceResolverStrategy}
     */
    public static final String EXPLICIT_ARTIFACT_VERSION = "apicurio.registry.artifact.version";

    /**
     * The URL of the Apicurio Registry. Required when using any Apicurio Registry serde class (serializer or
     * deserializer).
     */
    public static final String REGISTRY_URL = "apicurio.registry.url";

    /**
     * The API version implemented by the URL endpoint indicated by the 'apicurio.registry.url' property.
     * This is typically not required because the Registry Client will auto-detect the endpoint version
     * based on the URL itself.  However, in case the URL is non-standard or being rewritten by a proxy,
     * or some other odd circumstance, set the property to the API version such as "2" or "3".  Use the
     * major version only.
     */
    public static final String REGISTRY_URL_VERSION = "apicurio.registry.url.version";

    /**
     * The URL of the Token Endpoint.
     */
    public static final String AUTH_TOKEN_ENDPOINT = "apicurio.registry.auth.service.token.endpoint";

    /**
     * The Client Id of the Auth Service.
     */
    public static final String AUTH_CLIENT_ID = "apicurio.registry.auth.client.id";

    /**
     * The Secret of the Auth Service.
     */
    public static final String AUTH_CLIENT_SECRET = "apicurio.registry.auth.client.secret";

    /**
     * The Scope of the Auth Service.
     */
    public static final String AUTH_CLIENT_SCOPE = "apicurio.registry.auth.client.scope";

    /**
     * The Username of the Auth Service.
     */
    public static final String AUTH_USERNAME = "apicurio.registry.auth.username";

    /**
     * The Password of the Auth Service.
     */
    public static final String AUTH_PASSWORD = "apicurio.registry.auth.password";

    /**
     * Indicates how long to cache artifacts before auto-eviction. If not included, the artifact will be
     * fetched every time.
     */
    public static final String CHECK_PERIOD_MS = "apicurio.registry.check-period-ms";
    public static final long CHECK_PERIOD_MS_DEFAULT = 30000;

    /**
     * If a schema can not be retrieved from the Registry, serdes may retry a number of times. This
     * configuration option controls the number of retries before failing. Valid values are non-negative
     * integers.
     */
    public static final String RETRY_COUNT = "apicurio.registry.retry-count";
    public static final long RETRY_COUNT_DEFAULT = 3;

    /**
     * If a schema can not be be retrieved from the Registry, serdes may retry a number of times. This
     * configuration option controls the delay between the retry attempts, in milliseconds. Valid values are
     * non-negative integers.
     */
    public static final String RETRY_BACKOFF_MS = "apicurio.registry.retry-backoff-ms";
    public static final long RETRY_BACKOFF_MS_DEFAULT = 300;

    /**
     * Used to indicate the serdes to dereference the schema. This is used in two different situation, once
     * the schema is registered, instructs the serdes to ask the server for the schema dereferenced. It is
     * also used to instruct the serializer to dereference the schema before registering it Registry, but this
     * is only supported for Avro.
     */
    public static final String DEREFERENCE_SCHEMA = "apicurio.registry.dereference-schema";
    public static final boolean DEREFERENCE_DEFAULT = false;

    /**
     * Used to indicate whether the schema resolver should canonicalize content when using content
     * to lookup/search for an artifact version in the registry by content.
     */
    public static final String CANONICALIZE = "apicurio.registry.canonicalize";
    public static final boolean CANONICALIZE_DEFAULT = false;

    /**
     * The location of the trust store file for TLS/SSL connections. Can be a file path or a resource
     * on the classpath. Used when connecting to a registry over HTTPS with custom certificates.
     */
    public static final String TLS_TRUSTSTORE_LOCATION = "apicurio.registry.tls.truststore.location";

    /**
     * The password for the trust store file specified by {@link #TLS_TRUSTSTORE_LOCATION}.
     * Only required when using JKS trust stores.
     */
    public static final String TLS_TRUSTSTORE_PASSWORD = "apicurio.registry.tls.truststore.password";

    /**
     * The type of trust store. Valid values are "JKS" and "PEM". Defaults to "JKS".
     */
    public static final String TLS_TRUSTSTORE_TYPE = "apicurio.registry.tls.truststore.type";
    public static final String TLS_TRUSTSTORE_TYPE_DEFAULT = "JKS";

    /**
     * Comma-separated list of PEM certificate file paths to trust. An alternative to using
     * {@link #TLS_TRUSTSTORE_LOCATION} with a JKS file. When using this option, set
     * {@link #TLS_TRUSTSTORE_TYPE} to "PEM".
     */
    public static final String TLS_CERTIFICATES = "apicurio.registry.tls.certificates";

    /**
     * If true, disables all SSL/TLS certificate verification. This is insecure and should only be used
     * in development/testing environments. Defaults to false.
     */
    public static final String TLS_TRUST_ALL = "apicurio.registry.tls.trust-all";
    public static final boolean TLS_TRUST_ALL_DEFAULT = false;

    /**
     * If true, verifies that the hostname in the server certificate matches the server hostname.
     * Defaults to true. Set to false only when necessary (e.g., when using IP addresses instead of hostnames).
     */
    public static final String TLS_VERIFY_HOST = "apicurio.registry.tls.verify-host";
    public static final boolean TLS_VERIFY_HOST_DEFAULT = true;

    public String getRegistryUrl() {
        String registryUrl = getString(REGISTRY_URL);
        if (registryUrl != null) {
            try {
                URI uri = new URI(registryUrl);
                String userInfo = uri.getUserInfo();
                if (userInfo != null) {
                    String[] credentials = userInfo.split(":", 2);
                    if (credentials.length == 2) {
                        // Override username and password if provided in the URL
                        this.originals.put(AUTH_USERNAME, credentials[0]);
                        this.originals.put(AUTH_PASSWORD, credentials[1]);
                    }
                }
                // Return the URL without the user info
                return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment()).toString();
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException("Invalid registry URL: " + registryUrl, e);
            }
        }
        return registryUrl;
    }

    public String getRegistryUrlVersion() {
        return getString(REGISTRY_URL_VERSION);
    }

    public String getTokenEndpoint() {
        return getString(AUTH_TOKEN_ENDPOINT);
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

    public boolean resolveDereferenced() {
        return getBooleanOrFalse(DEREFERENCE_SCHEMA);
    }

    public boolean isCanonicalize() {
        return getBooleanOrFalse(CANONICALIZE);
    }

    public String getTlsTruststoreLocation() {
        return getString(TLS_TRUSTSTORE_LOCATION);
    }

    public String getTlsTruststorePassword() {
        return getString(TLS_TRUSTSTORE_PASSWORD);
    }

    public String getTlsTruststoreType() {
        return getString(TLS_TRUSTSTORE_TYPE);
    }

    public String getTlsCertificates() {
        return getString(TLS_CERTIFICATES);
    }

    public boolean getTlsTrustAll() {
        return getBooleanOrFalse(TLS_TRUST_ALL);
    }

    public boolean getTlsVerifyHost() {
        return getBoolean(TLS_VERIFY_HOST);
    }

    @Override
    protected Map<String, ?> getDefaults() {
        return DEFAULTS;
    }

    private static final Map<String, Object> DEFAULTS = Map.ofEntries(
            entry(CANONICALIZE, CANONICALIZE_DEFAULT),
            entry(ARTIFACT_RESOLVER_STRATEGY, ARTIFACT_RESOLVER_STRATEGY_DEFAULT),
            entry(AUTO_REGISTER_ARTIFACT, AUTO_REGISTER_ARTIFACT_DEFAULT),
            entry(AUTO_REGISTER_ARTIFACT_IF_EXISTS, AUTO_REGISTER_ARTIFACT_IF_EXISTS_DEFAULT),
            entry(CACHE_LATEST, CACHE_LATEST_DEFAULT),
            entry(FAULT_TOLERANT_REFRESH, FAULT_TOLERANT_REFRESH_DEFAULT),
            entry(FIND_LATEST_ARTIFACT, FIND_LATEST_ARTIFACT_DEFAULT),
            entry(CHECK_PERIOD_MS, CHECK_PERIOD_MS_DEFAULT), entry(RETRY_COUNT, RETRY_COUNT_DEFAULT),
            entry(RETRY_BACKOFF_MS, RETRY_BACKOFF_MS_DEFAULT),
            entry(DEREFERENCE_SCHEMA, DEREFERENCE_DEFAULT),
            entry(TLS_TRUSTSTORE_TYPE, TLS_TRUSTSTORE_TYPE_DEFAULT),
            entry(TLS_TRUST_ALL, TLS_TRUST_ALL_DEFAULT),
            entry(TLS_VERIFY_HOST, TLS_VERIFY_HOST_DEFAULT));
}
