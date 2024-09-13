package io.apicurio.registry.serde.config;

import io.apicurio.registry.serde.headers.DefaultHeadersHandler;
import io.apicurio.registry.serde.headers.HeadersHandler;
import io.apicurio.registry.serde.headers.SerdeHeaders;

public class KafkaSerdeConfig {

    /**
     * Boolean to indicate whether serde classes should pass Global Id information via message headers instead
     * of in the message payload.
     */
    public static final String ENABLE_HEADERS = "apicurio.registry.headers.enabled";
    public static final boolean ENABLE_HEADERS_DEFAULT = true;

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
     * {@link SerdeHeaders#HEADER_KEY_GROUP_ID}.
     */
    public static final String HEADER_KEY_GROUP_ID_OVERRIDE_NAME = "apicurio.registry.headers.key.groupId.name";
    /**
     * Used to override the Kafka message header name used to pass the groupId for the message value. Only
     * applicable when {@link KafkaSerdeConfig#ENABLE_HEADERS} is enabled. Default value is
     * {@link SerdeHeaders#HEADER_VALUE_GROUP_ID}.
     */
    public static final String HEADER_VALUE_GROUP_ID_OVERRIDE_NAME = "apicurio.registry.headers.value.groupId.name";
    /**
     * Used to override the Kafka message header name used to pass the artifactId for the message key. Only
     * applicable when {@link KafkaSerdeConfig#ENABLE_HEADERS} is enabled. Default value is
     * {@link SerdeHeaders#HEADER_KEY_ARTIFACT_ID}.
     */
    public static final String HEADER_KEY_ARTIFACT_ID_OVERRIDE_NAME = "apicurio.registry.headers.key.artifactId.name";
    /**
     * Used to override the Kafka message header name used to pass the artifactId for the message value. Only
     * applicable when {@link KafkaSerdeConfig#ENABLE_HEADERS} is enabled. Default value is
     * {@link SerdeHeaders#HEADER_VALUE_ARTIFACT_ID}.
     */
    public static final String HEADER_VALUE_ARTIFACT_ID_OVERRIDE_NAME = "apicurio.registry.headers.value.artifactId.name";
    /**
     * Used to override the Kafka message header name used to pass the version for the message key. Only
     * applicable when {@link KafkaSerdeConfig#ENABLE_HEADERS} is enabled. Default value is
     * {@link SerdeHeaders#HEADER_KEY_VERSION}.
     */
    public static final String HEADER_KEY_VERSION_OVERRIDE_NAME = "apicurio.registry.headers.key.version.name";
    /**
     * Used to override the Kafka message header name used to pass the version for the message value. Only
     * applicable when {@link KafkaSerdeConfig#ENABLE_HEADERS} is enabled. Default value is
     * {@link SerdeHeaders#HEADER_VALUE_VERSION}.
     */
    public static final String HEADER_VALUE_VERSION_OVERRIDE_NAME = "apicurio.registry.headers.value.version.name";
    /**
     * Used to override the Kafka message header name used to pass the globalId for the message key. Only
     * applicable when {@link KafkaSerdeConfig#ENABLE_HEADERS} is enabled. Default value is
     * {@link SerdeHeaders#HEADER_KEY_GLOBAL_ID}.
     */
    public static final String HEADER_KEY_GLOBAL_ID_OVERRIDE_NAME = "apicurio.registry.headers.key.globalId.name";
    /**
     * Used to override the Kafka message header name used to pass the globalId for the message value. Only
     * applicable when {@link KafkaSerdeConfig#ENABLE_HEADERS} is enabled. Default value is
     * {@link SerdeHeaders#HEADER_VALUE_GLOBAL_ID}.
     */
    public static final String HEADER_VALUE_GLOBAL_ID_OVERRIDE_NAME = "apicurio.registry.headers.value.globalId.name";
    /**
     * Used to override the Kafka message header name used to pass the contentId for the message key. Only
     * applicable when {@link KafkaSerdeConfig#ENABLE_HEADERS} is enabled. Default value is
     * {@link SerdeHeaders#HEADER_KEY_CONTENT_ID}.
     */
    public static final String HEADER_KEY_CONTENT_ID_OVERRIDE_NAME = "apicurio.registry.headers.key.contentId.name";
    /**
     * Used to override the Kafka message header name used to pass the contentId for the message value. Only
     * applicable when {@link KafkaSerdeConfig#ENABLE_HEADERS} is enabled. Default value is
     * {@link SerdeHeaders#HEADER_VALUE_CONTENT_ID}.
     */
    public static final String HEADER_VALUE_CONTENT_ID_OVERRIDE_NAME = "apicurio.registry.headers.value.contentId.name";
    /**
     * Used to override the Kafka message header name used to pass the contentHash for the message key. Only
     * applicable when {@link KafkaSerdeConfig#ENABLE_HEADERS} is enabled. Default value is
     * {@link SerdeHeaders#HEADER_KEY_CONTENT_HASH}.
     */
    public static final String HEADER_KEY_CONTENT_HASH_OVERRIDE_NAME = "apicurio.registry.headers.key.contentHash.name";
    /**
     * Used to override the Kafka message header name used to pass the contentHash for the message value. Only
     * applicable when {@link KafkaSerdeConfig#ENABLE_HEADERS} is enabled. Default value is
     * {@link SerdeHeaders#HEADER_VALUE_CONTENT_HASH}.
     */
    public static final String HEADER_VALUE_CONTENT_HASH_OVERRIDE_NAME = "apicurio.registry.headers.value.contentHash.name";
    /**
     * Used to override the Kafka message header name used to pass the message type for the message key. Only
     * applicable when {@link KafkaSerdeConfig#ENABLE_HEADERS} is enabled. Only used by the JSON Schema serde
     * classes. Default value is {@link SerdeHeaders#HEADER_KEY_MESSAGE_TYPE}.
     */
    public static final String HEADER_KEY_MESSAGE_TYPE_OVERRIDE_NAME = "apicurio.registry.headers.key.msgType.name";
    /**
     * Used to override the Kafka message header name used to pass the message type for the message value.
     * Only applicable when {@link KafkaSerdeConfig#ENABLE_HEADERS} is enabled. Only used by the JSON Schema
     * serde classes. Default value is {@link SerdeHeaders#HEADER_VALUE_MESSAGE_TYPE}.
     */
    public static final String HEADER_VALUE_MESSAGE_TYPE_OVERRIDE_NAME = "apicurio.registry.headers.value.msgType.name";

}
