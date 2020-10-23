/*
 * Copyright 2020 JBoss Inc
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

package io.apicurio.registry.utils.serde;

import java.util.Properties;

import io.apicurio.registry.client.request.RestClientConfig;
import io.apicurio.registry.utils.serde.strategy.ArtifactIdStrategy;
import io.apicurio.registry.utils.serde.strategy.GlobalIdStrategy;
import io.apicurio.registry.utils.serde.strategy.IdHandler;

/**
 * Contains all of the Serde configuration properties.  These are all the property names used when
 * configuring serde classes in Kafka apps via a {@link Properties} object.  Serde classes can be
 * used by creating them directly as well, in which case these property names are not relevant.
 * @author eric.wittmann@gmail.com
 */
public class SerdeConfig {

    /**
     * The URL of the Apicurio Registry.  Required when using any Apicurio Registry serde class (serializer or deserializer).
     */
    public static final String REGISTRY_URL = "apicurio.registry.url";

    /**
     * Fully qualified Java classname of a class that implements {@link IdHandler} and is responsible
     * for writing the schema's Global ID to the message payload.  Only used when "USE_HEADERS" is 
     * missing or 'false'.
     */
    public static final String ID_HANDLER = "apicurio.registry.id-handler";
    /**
     * Shortcut for enabling the Legacy (Confluent compatible) implementation of {@link IdHandler}.  Should
     * not be used with "ID_HANDLER".  The value should be 'true' or 'false'.
     */
    public static final String ENABLE_CONFLUENT_ID_HANDLER = "apicurio.registry.as-confluent";

    /**
     * Boolean to indicate whether serde classes should pass Global Id information via message headers
     * instead of in the message payload.  May not apply to all serde variants (for example, the JSON Schema
     * serde classes always pass information via the headers).
     */
    public static final String USE_HEADERS = "apicurio.registry.use.headers";

    /**
     * Fully qualified Java classname of a class that implements {@link ArtifactIdStrategy} and is
     * responsible for mapping between the Kafka serde information and an artifactId.  For example
     * there is a strategy to use the topic name as the schema's artifactId.  Only used by the 
     * <em>Serializer</em> serde class.
     */
    public static final String ARTIFACT_ID_STRATEGY = "apicurio.registry.artifact-id";
    /**
     * Fully qualified Java classname of a class that implements {@link GlobalIdStrategy} and is
     * responsible for resolving the schema to a unique globalId.  For example, there is a strategy
     * that simply uses the most recent version of an artifact as the schema.  Only used by the
     * <em>Serializer</em> serde class.
     */
    public static final String GLOBAL_ID_STRATEGY = "apicurio.registry.global-id";

    /**
     * Config prefix that allows configuration of arbitrary HTTP client request headers used by
     * the Registry REST Client in the serde class when communicating with the Registry.  For 
     * example, this could be used to pass authentication information:
     * 
     * <code>apicurio.registry.request.headers.Authorization=BASIC Y2tlbnQ6a3J5cHQwbnIwY2tzIQ==</code>
     */
    public static final String REQUEST_HEADERS_PREFIX = RestClientConfig.REGISTRY_REQUEST_HEADERS_PREFIX;
    /**
     * Location of a trust store to use when connecting to the registry via SSL.
     */
    public static final String REQUEST_TRUSTSTORE_LOCATION = RestClientConfig.REGISTRY_REQUEST_TRUSTSTORE_LOCATION;
    /**
     * Type of trust store to use when connecting to the registry via SSL.
     */
    public static final String REQUEST_TRUSTSTORE_TYPE = RestClientConfig.REGISTRY_REQUEST_TRUSTSTORE_TYPE;
    /**
     * Password of the trust store to use when connecting to the registry via SSL.
     */
    public static final String REQUEST_TRUSTSTORE_PASSWORD = RestClientConfig.REGISTRY_REQUEST_TRUSTSTORE_PASSWORD;
    /**
     * Location of a keystore to use when e.g. connecting to the registry via mTLS.
     */
    public static final String REQUEST_KEYSTORE_LOCATION = RestClientConfig.REGISTRY_REQUEST_KEYSTORE_LOCATION;
    /**
     * Type of keystore to use when e.g. connecting to the registry via mTLS.
     */
    public static final String REQUEST_KEYSTORE_TYPE = RestClientConfig.REGISTRY_REQUEST_KEYSTORE_TYPE;
    /**
     * Password of the keystore to use when e.g. connecting to the registry via mTLS.
     */
    public static final String REQUEST_KEYSTORE_PASSWORD = RestClientConfig.REGISTRY_REQUEST_KEYSTORE_PASSWORD;
    /**
     * Key password used when e.g. connecting to the registry via mTLS.
     */
    public static final String REQUEST_KEY_PASSWORD = RestClientConfig.REGISTRY_REQUEST_KEY_PASSWORD;

    /**
     * Used by the Avro serde classes to choose an {@link AvroEncoding}, for example <code>JSON</code> or
     * </code>BINARY</code>.  Serializer and Deserializer configuration must match.
     */
    public static final String AVRO_ENCODING = "apicurio.registry.avro.encoding";

    /**
     * Boolean used to enable or disable validation.  Not applicable to all serde classes.  For example, the
     * JSON Schema serde classes use this to enable or disable JSON Schema validation (unlike Avro, the JSON
     * Schema schema is not required to serialize/deserialize the message payload).
     */
    public static final String VALIDATION_ENABLED = "apicurio.registry.serde.validation-enabled";
    
    /**
     * Used to override the Kafka message header name used to pass the artifactId for the message key.  Only
     * applicable when <code>USE_HEADERS</code> is enabled.  Default value is {@link SerdeHeaders#HEADER_KEY_ARTIFACT_ID}.
     */
    public static final String HEADER_KEY_ARTIFACT_ID_OVERRIDE_NAME = "apicurio.registry.headers.key.artifactId.name";
    /**
     * Used to override the Kafka message header name used to pass the artifactId for the message value.  Only
     * applicable when <code>USE_HEADERS</code> is enabled.  Default value is {@link SerdeHeaders#HEADER_VALUE_ARTIFACT_ID}.
     */
    public static final String HEADER_VALUE_ARTIFACT_ID_OVERRIDE_NAME = "apicurio.registry.headers.value.artifactId.name";
    /**
     * Used to override the Kafka message header name used to pass the version for the message key.  Only
     * applicable when <code>USE_HEADERS</code> is enabled.  Default value is {@link SerdeHeaders#HEADER_KEY_VERSION}.
     */
    public static final String HEADER_KEY_VERSION_OVERRIDE_NAME = "apicurio.registry.headers.key.version.name";
    /**
     * Used to override the Kafka message header name used to pass the version for the message value.  Only
     * applicable when <code>USE_HEADERS</code> is enabled.  Default value is {@link SerdeHeaders#HEADER_VALUE_VERSION}.
     */
    public static final String HEADER_VALUE_VERSION_OVERRIDE_NAME = "apicurio.registry.headers.value.version.name";
    /**
     * Used to override the Kafka message header name used to pass the globalId for the message key.  Only
     * applicable when <code>USE_HEADERS</code> is enabled.  Default value is {@link SerdeHeaders#HEADER_KEY_GLOBAL_ID}.
     */
    public static final String HEADER_KEY_GLOBAL_ID_OVERRIDE_NAME = "apicurio.registry.headers.key.globalId.name";
    /**
     * Used to override the Kafka message header name used to pass the globalId for the message value.  Only
     * applicable when <code>USE_HEADERS</code> is enabled.  Default value is {@link SerdeHeaders#HEADER_VALUE_GLOBAL_ID}.
     */
    public static final String HEADER_VALUE_GLOBAL_ID_OVERRIDE_NAME = "apicurio.registry.headers.value.globalId.name";
    /**
     * Used to override the Kafka message header name used to pass the message type for the message key.  Only
     * applicable when <code>USE_HEADERS</code> is enabled.  Only used by the JSON Schema serde classes.
     * Default value is {@link SerdeHeaders#HEADER_KEY_MESSAGE_TYPE}.
     */
    public static final String HEADER_KEY_MESSAGE_TYPE_OVERRIDE_NAME = "apicurio.registry.headers.key.msgType.name";
    /**
     * Used to override the Kafka message header name used to pass the message type for the message value.  Only
     * applicable when <code>USE_HEADERS</code> is enabled.  Only used by the JSON Schema serde classes.
     * Default value is {@link SerdeHeaders#HEADER_VALUE_MESSAGE_TYPE}.
     */
    public static final String HEADER_VALUE_MESSAGE_TYPE_OVERRIDE_NAME = "apicurio.registry.headers.value.msgType.name";

}
