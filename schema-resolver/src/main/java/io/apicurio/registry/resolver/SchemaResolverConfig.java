package io.apicurio.registry.resolver;

import io.apicurio.registry.resolver.data.Metadata;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.resolver.strategy.DynamicArtifactReferenceResolverStrategy;

/**
 * Contains the {@link DefaultSchemaResolver} configuration properties.
 */
public class SchemaResolverConfig {

    /**
     * Fully qualified Java classname of a class that implements {@link ArtifactReferenceResolverStrategy} and is
     * responsible for mapping between the Record being resolved and an artifactId. For example
     * there is a strategy to use the topic name as the schema's artifactId. Only used by {@link SchemaResolver#resolveSchema(io.apicurio.registry.resolver.data.Record)}
     */
    public static final String ARTIFACT_RESOLVER_STRATEGY = "apicurio.registry.artifact-resolver-strategy";

    /**
     * Uses the ArtifactReference available for each record. Requires {@link Metadata#artifactReference()} to be set.
     * Note this default artifact resolver strategy differs in behavior from the classic Kafka serdes ArtifactResolverStrategy
     */
    public static final String ARTIFACT_RESOLVER_STRATEGY_DEFAULT = DynamicArtifactReferenceResolverStrategy.class.getName();


    /**
     * Optional, boolean to indicate whether serializer classes should attempt to create an artifact in the registry.
     * Note: JsonSchema serializer does not support this feature yet.
     */
    public static final String AUTO_REGISTER_ARTIFACT = "apicurio.registry.auto-register";
    public static final boolean AUTO_REGISTER_ARTIFACT_DEFAULT = false;

    /**
     * Optional, one of "IfExists" to indicate the behavior of the client when there is a conflict creating an artifact because the artifact already exists.
     */
    public static final String AUTO_REGISTER_ARTIFACT_IF_EXISTS = "apicurio.registry.auto-register.if-exists";
    public static final String AUTO_REGISTER_ARTIFACT_IF_EXISTS_DEFAULT = "RETURN_OR_UPDATE";

    /**
     * Optional, boolean to indicate whether serializer classes should attempt to find the latest artifact in the registry for the corresponding groupId/artifactId.
     * GroupId and artifactId are configured either via {@link ArtifactReferenceResolverStrategy} or via config properties such as {@link SchemaResolverConfig#EXPLICIT_ARTIFACT_ID}.
     */
    public static final String FIND_LATEST_ARTIFACT = "apicurio.registry.find-latest";
    public static final boolean FIND_LATEST_ARTIFACT_DEFAULT = false;

    /**
     * If {@code true}, will cache schema lookups that either have `latest` or no version specified.  Setting this to false
     * will effectively disable caching for schema lookups that do not specify a version.
     */
    public static final String CACHE_LATEST = "apicurio.registry.cache-latest";
    public static final boolean CACHE_LATEST_DEFAULT = true;

    /**
     * If {@code true}, will log exceptions instead of throwing them when an error occurs trying to refresh a schema
     * in the cache.  This is useful for production situations where a stale schema is better than completely failing
     * schema resolution.  Note that this will not impact trying of retries, as retries are attempted before this flag
     * is considered.
     */
    public static final String FAULT_TOLERANT_REFRESH = "apicurio.registry.fault-tolerant-refresh";
    public static final boolean FAULT_TOLERANT_REFRESH_DEFAULT = false;

    /**
     * Only applicable for serializers
     * Optional, set explicitly the groupId used for querying/creating an artifact.
     * Overrides the groupId returned by the {@link ArtifactReferenceResolverStrategy}
     */
    public static final String EXPLICIT_ARTIFACT_GROUP_ID = "apicurio.registry.artifact.group-id";

    /**
     * Only applicable for serializers
     * Optional, set explicitly the artifactId used for querying/creating an artifact.
     * Overrides the artifactId returned by the {@link ArtifactReferenceResolverStrategy}
     */
    public static final String EXPLICIT_ARTIFACT_ID = "apicurio.registry.artifact.artifact-id";

    /**
     * Only applicable for serializers
     * Optional, set explicitly the version used for querying/creating an artifact.
     * Overrides the version returned by the {@link ArtifactReferenceResolverStrategy}
     */
    public static final String EXPLICIT_ARTIFACT_VERSION = "apicurio.registry.artifact.version";

    /**
     * The URL of the Apicurio Registry.  Required when using any Apicurio Registry serde class (serializer or deserializer).
     */
    public static final String REGISTRY_URL = "apicurio.registry.url";

    /**
     * The URL of the Token Endpoint. Required when using any Apicurio Registry serde class (serializer or deserializer) against a secured Apicurio Registry and AUTH_SERVICE_URL is not specified.
     */
    public static final String AUTH_TOKEN_ENDPOINT = "apicurio.auth.service.token.endpoint";

    /**
     * The URL of the Auth Service. Required when using any Apicurio Registry serde class (serializer or deserializer) against a secured Apicurio Registry.
     */
    public static final String AUTH_SERVICE_URL = "apicurio.auth.service.url";
    public static final String AUTH_SERVICE_URL_TOKEN_ENDPOINT = "/realms/%s/protocol/openid-connect/token";

    /**
     * The Realm of the Auth Service.
     */
    public static final String AUTH_REALM = "apicurio.auth.realm";

    /**
     * The Client Id of the Auth Service.
     */
    public static final String AUTH_CLIENT_ID = "apicurio.auth.client.id";

    /**
     * The Secret of the Auth Service.
     */
    public static final String AUTH_CLIENT_SECRET = "apicurio.auth.client.secret";

    /**
     * The Scope of the Auth Service.
     */
    public static final String AUTH_CLIENT_SCOPE = "apicurio.auth.client.scope";

    /**
     * The Username of the Auth Service.
     */
    public static final String AUTH_USERNAME = "apicurio.auth.username";

    /**
     * The Password of the Auth Service.
     */
    public static final String AUTH_PASSWORD = "apicurio.auth.password";

    /**
     * Indicates how long to cache artifacts before auto-eviction. If not included, the artifact will be fetched every time.
     */
    public static final String CHECK_PERIOD_MS = "apicurio.registry.check-period-ms";
    public static final long CHECK_PERIOD_MS_DEFAULT = 30000;

    /**
     * If a schema can not be retrieved from the Registry, serdes may retry a number of times.
     * This configuration option controls the number of retries before failing.
     * Valid values are non-negative integers.
     */
    public static final String RETRY_COUNT = "apicurio.registry.retry-count";
    public static final long RETRY_COUNT_DEFAULT = 3;

    /**
     * If a schema can not be be retrieved from the Registry, serdes may retry a number of times.
     * This configuration option controls the delay between the retry attempts, in milliseconds.
     * Valid values are non-negative integers.
     */
    public static final String RETRY_BACKOFF_MS = "apicurio.registry.retry-backoff-ms";
    public static final long RETRY_BACKOFF_MS_DEFAULT = 300;

    /**
     * Used to indicate the auto-register feature to try to dereference the schema before registering it in Registry.
     * Only applicable when {@link SchemaResolverConfig#AUTO_REGISTER_ARTIFACT} is enabled.
     */
    public static final String DEREFERENCE_SCHEMA = "apicurio.registry.dereference-schema";
    public static final boolean DEREFERENCE_SCHEMA_DEFAULT = false;

}
