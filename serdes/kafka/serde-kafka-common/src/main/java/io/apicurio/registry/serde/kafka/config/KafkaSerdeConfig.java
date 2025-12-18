package io.apicurio.registry.serde.kafka.config;

import io.apicurio.registry.serde.kafka.headers.DefaultHeadersHandler;
import io.apicurio.registry.serde.kafka.headers.HeadersHandler;
import io.apicurio.registry.serde.kafka.headers.KafkaSerdeHeaders;

public class KafkaSerdeConfig {

    /**
     * Boolean to indicate whether serde classes should pass Global Id information via message headers instead
     * of in the message payload.
     */
    public static final String ENABLE_HEADERS = "apicurio.registry.headers.enabled";
    public static final boolean ENABLE_HEADERS_DEFAULT = false;

    /**
     * Fully qualified Java classname of a class that implements {@link HeadersHandler} and is responsible for
     * writing the schema's Global ID to the message headers. Only used when
     * {@link KafkaSerdeConfig#ENABLE_HEADERS} is 'true'.
     */
    public static final String HEADERS_HANDLER = "apicurio.registry.headers.handler";
    public static final String HEADERS_HANDLER_DEFAULT = DefaultHeadersHandler.class.getName();

    /**
     * Used to override the Kafka message header name used to pass the groupId for the message key. Only
     * applicable when {@link KafkaSerdeConfig#ENABLE_HEADERS} is enabled. Default value is
     * {@link KafkaSerdeHeaders#HEADER_KEY_GROUP_ID}.
     */
    public static final String HEADER_KEY_GROUP_ID_OVERRIDE_NAME = "apicurio.registry.headers.key.groupId.name";
    /**
     * Used to override the Kafka message header name used to pass the groupId for the message value. Only
     * applicable when {@link KafkaSerdeConfig#ENABLE_HEADERS} is enabled. Default value is
     * {@link KafkaSerdeHeaders#HEADER_VALUE_GROUP_ID}.
     */
    public static final String HEADER_VALUE_GROUP_ID_OVERRIDE_NAME = "apicurio.registry.headers.value.groupId.name";
    /**
     * Used to override the Kafka message header name used to pass the artifactId for the message key. Only
     * applicable when {@link KafkaSerdeConfig#ENABLE_HEADERS} is enabled. Default value is
     * {@link KafkaSerdeHeaders#HEADER_KEY_ARTIFACT_ID}.
     */
    public static final String HEADER_KEY_ARTIFACT_ID_OVERRIDE_NAME = "apicurio.registry.headers.key.artifactId.name";
    /**
     * Used to override the Kafka message header name used to pass the artifactId for the message value. Only
     * applicable when {@link KafkaSerdeConfig#ENABLE_HEADERS} is enabled. Default value is
     * {@link KafkaSerdeHeaders#HEADER_VALUE_ARTIFACT_ID}.
     */
    public static final String HEADER_VALUE_ARTIFACT_ID_OVERRIDE_NAME = "apicurio.registry.headers.value.artifactId.name";
    /**
     * Used to override the Kafka message header name used to pass the version for the message key. Only
     * applicable when {@link KafkaSerdeConfig#ENABLE_HEADERS} is enabled. Default value is
     * {@link KafkaSerdeHeaders#HEADER_KEY_VERSION}.
     */
    public static final String HEADER_KEY_VERSION_OVERRIDE_NAME = "apicurio.registry.headers.key.version.name";
    /**
     * Used to override the Kafka message header name used to pass the version for the message value. Only
     * applicable when {@link KafkaSerdeConfig#ENABLE_HEADERS} is enabled. Default value is
     * {@link KafkaSerdeHeaders#HEADER_VALUE_VERSION}.
     */
    public static final String HEADER_VALUE_VERSION_OVERRIDE_NAME = "apicurio.registry.headers.value.version.name";
    /**
     * Used to override the Kafka message header name used to pass the globalId for the message key. Only
     * applicable when {@link KafkaSerdeConfig#ENABLE_HEADERS} is enabled. Default value is
     * {@link KafkaSerdeHeaders#HEADER_KEY_GLOBAL_ID}.
     */
    public static final String HEADER_KEY_GLOBAL_ID_OVERRIDE_NAME = "apicurio.registry.headers.key.globalId.name";
    /**
     * Used to override the Kafka message header name used to pass the globalId for the message value. Only
     * applicable when {@link KafkaSerdeConfig#ENABLE_HEADERS} is enabled. Default value is
     * {@link KafkaSerdeHeaders#HEADER_VALUE_GLOBAL_ID}.
     */
    public static final String HEADER_VALUE_GLOBAL_ID_OVERRIDE_NAME = "apicurio.registry.headers.value.globalId.name";
    /**
     * Used to override the Kafka message header name used to pass the contentId for the message key. Only
     * applicable when {@link KafkaSerdeConfig#ENABLE_HEADERS} is enabled. Default value is
     * {@link KafkaSerdeHeaders#HEADER_KEY_CONTENT_ID}.
     */
    public static final String HEADER_KEY_CONTENT_ID_OVERRIDE_NAME = "apicurio.registry.headers.key.contentId.name";
    /**
     * Used to override the Kafka message header name used to pass the contentId for the message value. Only
     * applicable when {@link KafkaSerdeConfig#ENABLE_HEADERS} is enabled. Default value is
     * {@link KafkaSerdeHeaders#HEADER_VALUE_CONTENT_ID}.
     */
    public static final String HEADER_VALUE_CONTENT_ID_OVERRIDE_NAME = "apicurio.registry.headers.value.contentId.name";
    /**
     * Used to override the Kafka message header name used to pass the contentHash for the message key. Only
     * applicable when {@link KafkaSerdeConfig#ENABLE_HEADERS} is enabled. Default value is
     * {@link KafkaSerdeHeaders#HEADER_KEY_CONTENT_HASH}.
     */
    public static final String HEADER_KEY_CONTENT_HASH_OVERRIDE_NAME = "apicurio.registry.headers.key.contentHash.name";
    /**
     * Used to override the Kafka message header name used to pass the contentHash for the message value. Only
     * applicable when {@link KafkaSerdeConfig#ENABLE_HEADERS} is enabled. Default value is
     * {@link KafkaSerdeHeaders#HEADER_VALUE_CONTENT_HASH}.
     */
    public static final String HEADER_VALUE_CONTENT_HASH_OVERRIDE_NAME = "apicurio.registry.headers.value.contentHash.name";
    /**
     * Used to override the Kafka message header name used to pass the message type for the message key. Only
     * applicable when {@link KafkaSerdeConfig#ENABLE_HEADERS} is enabled. Only used by the JSON Schema serde
     * classes. Default value is {@link KafkaSerdeHeaders#HEADER_KEY_MESSAGE_TYPE}.
     */
    public static final String HEADER_KEY_MESSAGE_TYPE_OVERRIDE_NAME = "apicurio.registry.headers.key.msgType.name";
    /**
     * Used to override the Kafka message header name used to pass the message type for the message value.
     * Only applicable when {@link KafkaSerdeConfig#ENABLE_HEADERS} is enabled. Only used by the JSON Schema
     * serde classes. Default value is {@link KafkaSerdeHeaders#HEADER_VALUE_MESSAGE_TYPE}.
     */
    public static final String HEADER_VALUE_MESSAGE_TYPE_OVERRIDE_NAME = "apicurio.registry.headers.value.msgType.name";

    /**
     * Boolean to indicate whether the serializer should read the schema from message headers instead of
     * inferring it from the data. When enabled, the serializer will look for the schema content in the
     * header specified by {@link KafkaSerdeHeaders#HEADER_KEY_SCHEMA} or
     * {@link KafkaSerdeHeaders#HEADER_VALUE_SCHEMA} (depending on whether it's a key or value serializer).
     * This allows producers to explicitly specify the schema to use for registration, which is useful when
     * the data source knows the exact schema in advance (e.g., Apache Camel integration).
     */
    public static final String USE_SCHEMA_FROM_HEADERS = "apicurio.registry.use-schema-from-headers";
    public static final boolean USE_SCHEMA_FROM_HEADERS_DEFAULT = false;

    /**
     * Used to override the Kafka message header name used to pass the schema content for the message key.
     * Only applicable when {@link KafkaSerdeConfig#USE_SCHEMA_FROM_HEADERS} is enabled.
     * Default value is {@link KafkaSerdeHeaders#HEADER_KEY_SCHEMA}.
     */
    public static final String HEADER_KEY_SCHEMA_OVERRIDE_NAME = "apicurio.registry.headers.key.schema.name";

    /**
     * Used to override the Kafka message header name used to pass the schema content for the message value.
     * Only applicable when {@link KafkaSerdeConfig#USE_SCHEMA_FROM_HEADERS} is enabled.
     * Default value is {@link KafkaSerdeHeaders#HEADER_VALUE_SCHEMA}.
     */
    public static final String HEADER_VALUE_SCHEMA_OVERRIDE_NAME = "apicurio.registry.headers.value.schema.name";

    /**
     * Used to override the Kafka message header name used to pass the schema type for the message key.
     * The schema type should match one of the artifact types (e.g., "AVRO", "PROTOBUF", "JSON").
     * Only applicable when {@link KafkaSerdeConfig#USE_SCHEMA_FROM_HEADERS} is enabled.
     * Default value is {@link KafkaSerdeHeaders#HEADER_KEY_SCHEMA_TYPE}.
     */
    public static final String HEADER_KEY_SCHEMA_TYPE_OVERRIDE_NAME = "apicurio.registry.headers.key.schemaType.name";

    /**
     * Used to override the Kafka message header name used to pass the schema type for the message value.
     * The schema type should match one of the artifact types (e.g., "AVRO", "PROTOBUF", "JSON").
     * Only applicable when {@link KafkaSerdeConfig#USE_SCHEMA_FROM_HEADERS} is enabled.
     * Default value is {@link KafkaSerdeHeaders#HEADER_VALUE_SCHEMA_TYPE}.
     */
    public static final String HEADER_VALUE_SCHEMA_TYPE_OVERRIDE_NAME = "apicurio.registry.headers.value.schemaType.name";

}
