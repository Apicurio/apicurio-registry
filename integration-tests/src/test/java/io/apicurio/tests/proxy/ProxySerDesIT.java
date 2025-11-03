package io.apicurio.tests.proxy;

import io.apicurio.registry.resolver.config.SchemaResolverConfig;
import io.apicurio.registry.rest.client.models.IfArtifactExists;
import io.apicurio.registry.serde.config.SerdeConfig;
import io.apicurio.registry.serde.jsonschema.JsonSchemaKafkaDeserializer;
import io.apicurio.registry.serde.jsonschema.JsonSchemaKafkaSerializer;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.apicurio.tests.serdes.apicurio.JsonSchemaMsgFactory;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Integration tests for proxy configuration with Apicurio Registry SerDes (Serializers/Deserializers).
 * Tests verify that serializers and deserializers can successfully connect through an HTTP proxy.
 */
@QuarkusIntegrationTest
public class ProxySerDesIT extends ApicurioRegistryBaseIT {

    private static final int PROXY_PORT = 30003;
    private TrackingProxy proxy;

    @BeforeEach
    public void setupProxy() throws Exception {
        URI registryUri = URI.create(getRegistryV3ApiUrl());
        String host = registryUri.getHost();
        int port = registryUri.getPort();
        if (port == -1) {
            // Default port based on scheme
            port = "https".equals(registryUri.getScheme()) ? 443 : 80;
        }

        logger.info("Setting up proxy for SerDes tests to {}:{}", host, port);
        proxy = new TrackingProxy(host, port);
        proxy.start().get();
        proxy.resetRequestCount();
    }

    @AfterEach
    public void teardownProxy() {
        if (proxy != null) {
            proxy.stop();
        }
    }

    /**
     * Test that JSON Schema serializer/deserializer work through proxy
     */
    @Test
    public void testJsonSchemaSerDeWithProxy() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        // Create the schema first using direct client (without proxy)
        String schemaContent = "{\"type\":\"record\",\"name\":\"TestRecord\",\"fields\":[{\"name\":\"message\",\"type\":\"string\"}]}";
        createArtifact(groupId, artifactId, ArtifactType.JSON, schemaContent, ContentTypes.APPLICATION_JSON,
                IfArtifactExists.FAIL, null);

        // Create message to serialize
        JsonSchemaMsgFactory msgFactory = new JsonSchemaMsgFactory();
        Object message = msgFactory.generateMessage(0);

        try (JsonSchemaKafkaSerializer<Object> serializer = new JsonSchemaKafkaSerializer<>();
             JsonSchemaKafkaDeserializer<Object> deserializer = new JsonSchemaKafkaDeserializer<>()) {

            // Configure serializer with proxy settings
            Map<String, Object> serializerConfig = new HashMap<>();
            serializerConfig.put(SerdeConfig.REGISTRY_URL, getRegistryV3ApiUrl());
            serializerConfig.put(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId);
            serializerConfig.put(SerdeConfig.EXPLICIT_ARTIFACT_ID, artifactId);
            // Proxy configuration
            serializerConfig.put(SchemaResolverConfig.PROXY_HOST, "localhost");
            serializerConfig.put(SchemaResolverConfig.PROXY_PORT, PROXY_PORT);

            serializer.configure(serializerConfig, false);

            // Configure deserializer with proxy settings
            Map<String, Object> deserializerConfig = new HashMap<>();
            deserializerConfig.put(SerdeConfig.REGISTRY_URL, getRegistryV3ApiUrl());
            // Proxy configuration
            deserializerConfig.put(SchemaResolverConfig.PROXY_HOST, "localhost");
            deserializerConfig.put(SchemaResolverConfig.PROXY_PORT, PROXY_PORT);

            deserializer.configure(deserializerConfig, false);

            // Serialize the message (should go through proxy)
            Headers headers = new RecordHeaders();
            byte[] serializedData = serializer.serialize(artifactId, headers, message);
            Assertions.assertNotNull(serializedData);

            int requestCountAfterSerialize = proxy.getRequestCount();
            Assertions.assertTrue(requestCountAfterSerialize > 0,
                    "Expected serializer to make requests through proxy");
            logger.info("Requests through proxy after serialization: {}", requestCountAfterSerialize);

            // Deserialize the message (should go through proxy)
            Object deserializedMessage = deserializer.deserialize(artifactId, headers, serializedData);
            Assertions.assertNotNull(deserializedMessage);

            int requestCountAfterDeserialize = proxy.getRequestCount();
            Assertions.assertTrue(requestCountAfterDeserialize >= requestCountAfterSerialize,
                    "Expected deserializer to make requests through proxy");
            logger.info("Total requests through proxy: {}", requestCountAfterDeserialize);
        }
    }

    /**
     * Test that SerDes work with proxy using string port configuration
     */
    @Test
    public void testJsonSchemaSerDeWithProxyStringPort() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        // Create the schema
        String schemaContent = "{\"type\":\"record\",\"name\":\"TestRecord2\",\"fields\":[{\"name\":\"value\",\"type\":\"int\"}]}";
        createArtifact(groupId, artifactId, ArtifactType.JSON, schemaContent, ContentTypes.APPLICATION_JSON,
                IfArtifactExists.FAIL, null);

        JsonSchemaMsgFactory msgFactory = new JsonSchemaMsgFactory();
        Object message = msgFactory.generateMessage(1);

        try (JsonSchemaKafkaSerializer<Object> serializer = new JsonSchemaKafkaSerializer<>();
             JsonSchemaKafkaDeserializer<Object> deserializer = new JsonSchemaKafkaDeserializer<>()) {

            // Configure with proxy using String port value
            Map<String, Object> config = new HashMap<>();
            config.put(SerdeConfig.REGISTRY_URL, getRegistryV3ApiUrl());
            config.put(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId);
            config.put(SerdeConfig.EXPLICIT_ARTIFACT_ID, artifactId);
            config.put(SchemaResolverConfig.PROXY_HOST, "localhost");
            config.put(SchemaResolverConfig.PROXY_PORT, String.valueOf(PROXY_PORT)); // String port

            serializer.configure(config, false);

            Map<String, Object> deserializerConfig = new HashMap<>();
            deserializerConfig.put(SerdeConfig.REGISTRY_URL, getRegistryV3ApiUrl());
            deserializerConfig.put(SchemaResolverConfig.PROXY_HOST, "localhost");
            deserializerConfig.put(SchemaResolverConfig.PROXY_PORT, String.valueOf(PROXY_PORT)); // String port

            deserializer.configure(deserializerConfig, false);

            // Perform serialize/deserialize
            Headers headers = new RecordHeaders();
            byte[] data = serializer.serialize(artifactId, headers, message);
            Object result = deserializer.deserialize(artifactId, headers, data);

            Assertions.assertNotNull(result);
            Assertions.assertTrue(proxy.getRequestCount() > 0,
                    "Expected requests through proxy with string port configuration");
        }
    }

    /**
     * Test that SerDes work without proxy when not configured
     */
    @Test
    public void testJsonSchemaSerDeWithoutProxy() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        // Create the schema
        String schemaContent = "{\"type\":\"record\",\"name\":\"TestRecord3\",\"fields\":[{\"name\":\"id\",\"type\":\"string\"}]}";
        createArtifact(groupId, artifactId, ArtifactType.JSON, schemaContent, ContentTypes.APPLICATION_JSON,
                IfArtifactExists.FAIL, null);

        JsonSchemaMsgFactory msgFactory = new JsonSchemaMsgFactory();
        Object message = msgFactory.generateMessage(2);

        try (JsonSchemaKafkaSerializer<Object> serializer = new JsonSchemaKafkaSerializer<>();
             JsonSchemaKafkaDeserializer<Object> deserializer = new JsonSchemaKafkaDeserializer<>()) {

            // Configure WITHOUT proxy
            Map<String, Object> config = new HashMap<>();
            config.put(SerdeConfig.REGISTRY_URL, getRegistryV3ApiUrl());
            config.put(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId);
            config.put(SerdeConfig.EXPLICIT_ARTIFACT_ID, artifactId);
            // No proxy configuration

            serializer.configure(config, false);

            Map<String, Object> deserializerConfig = new HashMap<>();
            deserializerConfig.put(SerdeConfig.REGISTRY_URL, getRegistryV3ApiUrl());
            // No proxy configuration

            deserializer.configure(deserializerConfig, false);

            // Perform serialize/deserialize
            Headers headers = new RecordHeaders();
            byte[] data = serializer.serialize(artifactId, headers, message);
            Object result = deserializer.deserialize(artifactId, headers, data);

            Assertions.assertNotNull(result);

            // Verify NO requests went through proxy
            Assertions.assertEquals(0, proxy.getRequestCount(),
                    "Expected no requests through proxy when not configured");
        }
    }
}
