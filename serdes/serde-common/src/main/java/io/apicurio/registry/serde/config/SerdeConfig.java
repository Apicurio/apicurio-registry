package io.apicurio.registry.serde.config;

import io.apicurio.registry.resolver.DefaultSchemaResolver;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.SchemaResolverConfig;
import io.apicurio.registry.resolver.config.AbstractConfig;
import io.apicurio.registry.rest.v2.beans.IfExists;
import io.apicurio.registry.serde.Default4ByteIdHandler;
import io.apicurio.registry.serde.IdHandler;
import io.apicurio.registry.serde.fallback.DefaultFallbackArtifactProvider;
import io.apicurio.registry.serde.fallback.FallbackArtifactProvider;
import io.apicurio.registry.serde.strategy.ArtifactResolverStrategy;
import io.apicurio.registry.serde.strategy.TopicIdStrategy;

import java.util.Map;
import java.util.Properties;

import static java.util.Map.entry;

/**
 * Contains all of the Serde configuration properties. These are all the property names used when configuring
 * serde classes in Kafka apps via a {@link Properties} object. Serde classes can be used by creating them
 * directly as well, in which case these property names are not relevant.
 */
public class SerdeConfig extends AbstractConfig {

    public SerdeConfig(Map<String, ?> originals) {
        this.originals = originals;
    }

    public SerdeConfig() {
        this.originals = DEFAULTS;
    }

    public Object getIdHandler() {
        return this.getObject(ID_HANDLER);
    }

    public IdOption useIdOption() {
        return IdOption.valueOf(this.getString(USE_ID));
    }

    @Override
    protected Map<String, ?> getDefaults() {
        return DEFAULTS;
    }

    /**
     * Fully qualified Java classname of a class that implements {@link ArtifactResolverStrategy} and is
     * responsible for mapping between the Kafka serde information and an artifactId. For example there is a
     * strategy to use the topic name as the schema's artifactId. Only used by the <em>Serializer</em> serde
     * class.
     */
    public static final String ARTIFACT_RESOLVER_STRATEGY = SchemaResolverConfig.ARTIFACT_RESOLVER_STRATEGY;
    public static final String ARTIFACT_RESOLVER_STRATEGY_DEFAULT = TopicIdStrategy.class.getName();

    /**
     * Fully qualified Java classname of a class that implements {@link SchemaResolver}.
     * {@link DefaultSchemaResolver} is used by default. The SchemaResolver is used both by Serializer and
     * Deserializer classes.
     */
    public static final String SCHEMA_RESOLVER = "apicurio.registry.schema-resolver";
    public static final String SCHEMA_RESOLVER_DEFAULT = DefaultSchemaResolver.class.getName();

    /**
     * Property used internally to mark that a component is being configured for a kafka message key.
     */
    public static final String IS_KEY = "apicurio.registry.is-key";

    /**
     * Optional, boolean to indicate whether serializer classes should attempt to create an artifact in the
     * registry. Note: JsonSchema serializer does not support this feature yet.
     */
    public static final String AUTO_REGISTER_ARTIFACT = SchemaResolverConfig.AUTO_REGISTER_ARTIFACT;
    public static final boolean AUTO_REGISTER_ARTIFACT_DEFAULT = SchemaResolverConfig.AUTO_REGISTER_ARTIFACT_DEFAULT;

    /**
     * Optional, one of {@link IfExists} to indicate the behavior of the client when there is a conflict
     * creating an artifact because the artifact already exists.
     */
    public static final String AUTO_REGISTER_ARTIFACT_IF_EXISTS = SchemaResolverConfig.AUTO_REGISTER_ARTIFACT_IF_EXISTS;
    public static final String AUTO_REGISTER_ARTIFACT_IF_EXISTS_DEFAULT = SchemaResolverConfig.AUTO_REGISTER_ARTIFACT_IF_EXISTS_DEFAULT;

    /**
     * Optional, boolean to indicate whether serializer classes should attempt to find the latest artifact in
     * the registry for the corresponding groupId/artifactId. GroupId and artifactId are configured either via
     * {@link ArtifactResolverStrategy} or via config properties such as
     * {@link SerdeConfig#EXPLICIT_ARTIFACT_ID}.
     */
    public static final String FIND_LATEST_ARTIFACT = SchemaResolverConfig.FIND_LATEST_ARTIFACT;
    public static final boolean FIND_LATEST_ARTIFACT_DEFAULT = SchemaResolverConfig.FIND_LATEST_ARTIFACT_DEFAULT;

    /**
     * Only applicable for serializers Optional, set explicitly the groupId used for querying/creating an
     * artifact. Overrides the groupId returned by the {@link ArtifactResolverStrategy}
     */
    public static final String EXPLICIT_ARTIFACT_GROUP_ID = SchemaResolverConfig.EXPLICIT_ARTIFACT_GROUP_ID;

    /**
     * Only applicable for serializers Optional, set explicitly the artifactId used for querying/creating an
     * artifact. Overrides the artifactId returned by the {@link ArtifactResolverStrategy}
     */
    public static final String EXPLICIT_ARTIFACT_ID = SchemaResolverConfig.EXPLICIT_ARTIFACT_ID;

    /**
     * Only applicable for serializers Optional, set explicitly the schema used for serialization.
     */
    public static final String SCHEMA_LOCATION = SchemaResolverConfig.SCHEMA_LOCATION;

    /**
     * Only applicable for serializers Optional, set explicitly the version used for querying/creating an
     * artifact. Overrides the version returned by the {@link ArtifactResolverStrategy}
     */
    public static final String EXPLICIT_ARTIFACT_VERSION = SchemaResolverConfig.EXPLICIT_ARTIFACT_VERSION;

    /**
     * The URL of the Apicurio Registry. Required when using any Apicurio Registry serde class (serializer or
     * deserializer).
     */
    public static final String REGISTRY_URL = SchemaResolverConfig.REGISTRY_URL;

    /**
     * The URL of the Token Endpoint. Required when using any Apicurio Registry serde class (serializer or
     * deserializer) against a secured Apicurio Registry and AUTH_SERVICE_URL is not specified.
     */
    public static final String AUTH_TOKEN_ENDPOINT = SchemaResolverConfig.AUTH_TOKEN_ENDPOINT;

    /**
     * The URL of the Auth Service. Required when using any Apicurio Registry serde class (serializer or
     * deserializer) against a secured Apicurio Registry.
     */
    public static final String AUTH_SERVICE_URL = SchemaResolverConfig.AUTH_SERVICE_URL;
    public static final String AUTH_SERVICE_URL_TOKEN_ENDPOINT = SchemaResolverConfig.AUTH_SERVICE_URL_TOKEN_ENDPOINT;

    /**
     * The Realm of the Auth Service.
     */
    public static final String AUTH_REALM = SchemaResolverConfig.AUTH_REALM;

    /**
     * The Client Id of the Auth Service.
     */
    public static final String AUTH_CLIENT_ID = SchemaResolverConfig.AUTH_CLIENT_ID;

    /**
     * The Secret of the Auth Service.
     */
    public static final String AUTH_CLIENT_SECRET = SchemaResolverConfig.AUTH_CLIENT_SECRET;

    /**
     * The Username of the Auth Service.
     */
    public static final String AUTH_USERNAME = SchemaResolverConfig.AUTH_USERNAME;

    /**
     * The Password of the Auth Service.
     */
    public static final String AUTH_PASSWORD = SchemaResolverConfig.AUTH_PASSWORD;

    /**
     * Fully qualified Java classname of a class that implements {@link IdHandler} and is responsible for
     * writing the schema's Global ID to the message payload.
     */
    public static final String ID_HANDLER = "apicurio.registry.id-handler";
    public static final String ID_HANDLER_DEFAULT = Default4ByteIdHandler.class.getName();

    /**
     * Indicates how long to cache artifacts before auto-eviction. If not included, the artifact will be
     * fetched every time.
     */
    public static final String CHECK_PERIOD_MS = SchemaResolverConfig.CHECK_PERIOD_MS;
    public static final long CHECK_PERIOD_MS_DEFAULT = SchemaResolverConfig.CHECK_PERIOD_MS_DEFAULT;

    /**
     * If a schema can not be retrieved from the Registry, serdes may retry a number of times. This
     * configuration option controls the number of retries before failing. Valid values are non-negative
     * integers.
     */
    public static final String RETRY_COUNT = SchemaResolverConfig.RETRY_COUNT;
    public static final long RETRY_COUNT_DEFAULT = SchemaResolverConfig.RETRY_COUNT_DEFAULT;

    /**
     * If a schema can not be be retrieved from the Registry, serdes may retry a number of times. This
     * configuration option controls the delay between the retry attempts, in milliseconds. Valid values are
     * non-negative integers.
     */
    public static final String RETRY_BACKOFF_MS = SchemaResolverConfig.RETRY_BACKOFF_MS;
    public static final long RETRY_BACKOFF_MS_DEFAULT = SchemaResolverConfig.RETRY_BACKOFF_MS_DEFAULT;

    /**
     * Configures the serdes to use the specified {@link IdOption} as the identifier for the artifacts.
     * Instructs the serializer to write the specified id into the kafka records and instructs the
     * deserializer to read and use the specified id from the kafka records (to find the schema).
     */
    public static final String USE_ID = "apicurio.registry.use-id";
    public static final String USE_ID_DEFAULT = IdOption.contentId.name();

    /**
     * Boolean used to enable or disable validation. Not applicable to all serde classes. For example, the
     * JSON Schema serde classes use this to enable or disable JSON Schema validation (unlike Avro, the JSON
     * Schema schema is not required to serialize/deserialize the message payload).
     */
    public static final String VALIDATION_ENABLED = "apicurio.registry.serde.validation-enabled";
    public static final boolean VALIDATION_ENABLED_DEFAULT = true;

    /**
     * Only applicable for deserializers Optional, set explicitly the groupId used as fallback for resolving
     * the artifact used for deserialization.
     */
    public static final String FALLBACK_ARTIFACT_GROUP_ID = "apicurio.registry.fallback.group-id";

    /**
     * Only applicable for deserializers Optional, set explicitly the artifactId used as fallback for
     * resolving the artifact used for deserialization.
     */
    public static final String FALLBACK_ARTIFACT_ID = "apicurio.registry.fallback.artifact-id";

    /**
     * Only applicable for deserializers Optional, set explicitly the version used as fallback for resolving
     * the artifact used for deserialization.
     */
    public static final String FALLBACK_ARTIFACT_VERSION = "apicurio.registry.fallback.version";

    /**
     * Only applicable for deserializers Optional, allows to set a custom implementation of
     * {@link FallbackArtifactProvider} , for resolving the artifact used for deserialization.
     */
    public static final String FALLBACK_ARTIFACT_PROVIDER = "apicurio.registry.fallback.provider";
    public static final String FALLBACK_ARTIFACT_PROVIDER_DEFAULT = DefaultFallbackArtifactProvider.class
            .getName();

    /**
     * Fully qualified Java classname of a class that will be used as the return type for the deserializer.
     * Aplicable for keys deserialization. Forces the deserializer to return objects of this type, if not
     * present the return type will be obtained from the message headers, if updated by the serializer.
     * Supported by JsonSchema and Protobuf deserializers.
     */
    public static final String DESERIALIZER_SPECIFIC_KEY_RETURN_CLASS = "apicurio.registry.deserializer.key.return-class";

    /**
     * Fully qualified Java classname of a class that will be used as the return type for the deserializer.
     * Aplicable for values deserialization. Forces the deserializer to return objects of this type, if not
     * present the return type will be obtained from the message headers, if updated by the serializer.
     * Supported by JsonSchema and Protobuf deserializers.
     */
    public static final String DESERIALIZER_SPECIFIC_VALUE_RETURN_CLASS = "apicurio.registry.deserializer.value.return-class";

    private static final Map<String, Object> DEFAULTS = Map.ofEntries(entry(ID_HANDLER, ID_HANDLER_DEFAULT),
            entry(USE_ID, USE_ID_DEFAULT));

}
