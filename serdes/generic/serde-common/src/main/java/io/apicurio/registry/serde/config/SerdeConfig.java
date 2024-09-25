package io.apicurio.registry.serde.config;

import io.apicurio.registry.resolver.DefaultSchemaResolver;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.config.SchemaResolverConfig;
import io.apicurio.registry.resolver.data.Metadata;
import io.apicurio.registry.serde.Default4ByteIdHandler;
import io.apicurio.registry.serde.IdHandler;
import io.apicurio.registry.serde.fallback.DefaultFallbackArtifactProvider;
import io.apicurio.registry.serde.fallback.FallbackArtifactProvider;
import io.apicurio.registry.serde.strategy.TopicIdStrategy;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static java.util.Map.entry;

/**
 * Contains all of the Serde configuration properties. These are all the property names used when configuring
 * serde classes in Kafka apps via a {@link Properties} object. Serde classes can be used by creating them
 * directly as well, in which case these property names are not relevant.
 */
public class SerdeConfig extends SchemaResolverConfig {

    public SerdeConfig(Map<String, ?> originals) {
        Map<String, Object> joint = new HashMap<>(getDefaults());
        joint.putAll(originals);
        this.originals = joint;
    }

    public SerdeConfig() {
        super(DEFAULTS);
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
     * Fully qualified Java classname of a class that implements {@link SchemaResolver}.
     * {@link DefaultSchemaResolver} is used by default. The SchemaResolver is used both by Serializer and
     * Deserializer classes.
     */
    public static final String SCHEMA_RESOLVER = "apicurio.registry.schema-resolver";
    public static final String SCHEMA_RESOLVER_DEFAULT = DefaultSchemaResolver.class.getName();

    /**
     * Uses the ArtifactReference available for each record. Requires {@link Metadata#artifactReference()} to
     * be set.
     */
    public static final String ARTIFACT_RESOLVER_STRATEGY_DEFAULT = TopicIdStrategy.class.getName();

    /**
     * Property used internally to mark that a component is being configured for a kafka message key.
     */
    public static final String IS_KEY = "apicurio.registry.is-key";

    /**
     * Fully qualified Java classname of a class that implements {@link IdHandler} and is responsible for
     * writing the schema's Global ID to the message payload.
     */
    public static final String ID_HANDLER = "apicurio.registry.id-handler";
    public static final String ID_HANDLER_DEFAULT = Default4ByteIdHandler.class.getName();

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
