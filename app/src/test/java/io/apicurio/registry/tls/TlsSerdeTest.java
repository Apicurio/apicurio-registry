package io.apicurio.registry.tls;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.RegistryClientOptions;
import io.apicurio.registry.resolver.config.SchemaResolverConfig;
import io.apicurio.registry.serde.config.KafkaSerdeConfig;
import io.apicurio.registry.serde.config.SerdeConfig;
import io.apicurio.registry.serde.jsonschema.JsonSchemaKafkaDeserializer;
import io.apicurio.registry.serde.jsonschema.JsonSchemaKafkaSerializer;
import io.apicurio.registry.serde.strategy.SimpleTopicIdStrategy;
import io.apicurio.registry.support.Person;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.vertx.core.Vertx;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.Deserializer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLHandshakeException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Tests that SerDe classes (serializers/deserializers) work with TLS/SSL configuration
 * when connecting to an HTTPS-enabled registry with self-signed certificates.
 */
@QuarkusTest
@TestProfile(TlsTestProfile.class)
public class TlsSerdeTest extends AbstractResourceTestBase {

    @TestHTTPResource(value = "/apis", tls = true)
    URL httpsUrl;

    @Override
    protected void deleteGlobalRules(int expectedDefaultRulesCount) throws Exception {
        // Don't bother with this test
    }

    @Override
    @BeforeAll
    protected void beforeAll() throws Exception {
        vertx = Vertx.vertx();

        // Override base URL to use HTTPS
        registryApiBaseUrl = httpsUrl.toExternalForm();
        registryV2ApiUrl = registryApiBaseUrl + "/registry/v2";
        registryV3ApiUrl = registryApiBaseUrl + "/registry/v3";

        clientV3 = RegistryClientFactory.create(RegistryClientOptions.create()
                .registryUrl(registryV3ApiUrl)
                .trustAll(true)
                .vertx(vertx));
    }

    /**
     * Test JsonSchema SerDe with JKS trust store
     */
    @Test
    public void testJsonSchemaSerdeWithJksTrustStore() throws Exception {
        URL truststoreUrl = getClass().getClassLoader().getResource("tls/registry-truststore.jks");
        Assertions.assertNotNull(truststoreUrl, "Truststore file not found");

        InputStream jsonSchema = getClass()
                .getResourceAsStream("/io/apicurio/registry/util/json-schema.json");
        Assertions.assertNotNull(jsonSchema);

        String groupId = TestUtils.generateGroupId();
        String artifactId = generateArtifactId();

        createArtifact(groupId, artifactId, ArtifactType.JSON, IoUtil.toString(jsonSchema),
                ContentTypes.APPLICATION_JSON);

        Person person = new Person("Alice", "Smith", 25);

        try (JsonSchemaKafkaSerializer<Person> serializer = new JsonSchemaKafkaSerializer<>();
            Deserializer<Person> deserializer = new JsonSchemaKafkaDeserializer<>()) {

            // Common SerDe configuration
            Map<String, Object> commonConfig = new HashMap<>();
            commonConfig.put(SerdeConfig.REGISTRY_URL, registryV3ApiUrl);
            // TLS configuration with JKS trust store
            commonConfig.put(SchemaResolverConfig.TLS_TRUSTSTORE_LOCATION, truststoreUrl.getPath());
            commonConfig.put(SchemaResolverConfig.TLS_TRUSTSTORE_PASSWORD, "registrytest");
            commonConfig.put(SchemaResolverConfig.TLS_TRUSTSTORE_TYPE, "JKS");

            // Configure the serializer
            Map<String, Object> serializerConfig = new HashMap<>();
            serializerConfig.putAll(commonConfig);
            serializerConfig.put(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId);
            serializerConfig.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, SimpleTopicIdStrategy.class.getName());
            serializerConfig.put(KafkaSerdeConfig.ENABLE_HEADERS, "true");
            serializerConfig.put(SerdeConfig.VALIDATION_ENABLED, "true");
            serializer.configure(serializerConfig, false);

            // Configure the deserializer
            Map<String, Object> deserializerConfig = new HashMap<>();
            deserializerConfig.putAll(commonConfig);
            deserializerConfig.put(KafkaSerdeConfig.ENABLE_HEADERS, "true");
            deserializer.configure(deserializerConfig, false);

            // Serialize/deserialize the message
            Headers headers = new RecordHeaders();
            byte[] bytes = serializer.serialize(artifactId, headers, person);
            person = deserializer.deserialize(artifactId, headers, bytes);

            Assertions.assertEquals("Alice", person.getFirstName());
            Assertions.assertEquals("Smith", person.getLastName());
            Assertions.assertEquals(25, person.getAge());
        }
    }

    /**
     * Test JsonSchema SerDe with PEM certificate
     */
    @Test
    public void testJsonSchemaSerdeWithPemCertificate() throws Exception {
        URL certUrl = getClass().getClassLoader().getResource("tls/registry-cert.pem");
        Assertions.assertNotNull(certUrl, "PEM certificate file not found");

        InputStream jsonSchema = getClass()
                .getResourceAsStream("/io/apicurio/registry/util/json-schema.json");
        Assertions.assertNotNull(jsonSchema);

        String groupId = TestUtils.generateGroupId();
        String artifactId = generateArtifactId();

        createArtifact(groupId, artifactId, ArtifactType.JSON, IoUtil.toString(jsonSchema),
                ContentTypes.APPLICATION_JSON);

        Person person = new Person("Bob", "Jones", 30);

        try (JsonSchemaKafkaSerializer<Person> serializer = new JsonSchemaKafkaSerializer<>();
            Deserializer<Person> deserializer = new JsonSchemaKafkaDeserializer<>()) {

            // Common SerDe configuration
            Map<String, Object> commonConfig = new HashMap<>();
            commonConfig.put(SerdeConfig.REGISTRY_URL, registryV3ApiUrl);
            // TLS configuration with PEM certificate
            commonConfig.put(SchemaResolverConfig.TLS_TRUSTSTORE_TYPE, "PEM");
            commonConfig.put(SchemaResolverConfig.TLS_CERTIFICATES, certUrl.getPath());

            // Configure the serializer
            Map<String, Object> serializerConfig = new HashMap<>();
            serializerConfig.putAll(commonConfig);
            serializerConfig.put(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId);
            serializerConfig.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, SimpleTopicIdStrategy.class.getName());
            serializerConfig.put(KafkaSerdeConfig.ENABLE_HEADERS, "true");
            serializerConfig.put(SerdeConfig.VALIDATION_ENABLED, "true");
            serializer.configure(serializerConfig, false);

            // Configure the deserializer
            Map<String, Object> deserializerConfig = new HashMap<>();
            deserializerConfig.putAll(commonConfig);
            deserializerConfig.put(KafkaSerdeConfig.ENABLE_HEADERS, "true");
            deserializer.configure(deserializerConfig, false);

            Headers headers = new RecordHeaders();
            byte[] bytes = serializer.serialize(artifactId, headers, person);

            person = deserializer.deserialize(artifactId, headers, bytes);

            Assertions.assertEquals("Bob", person.getFirstName());
            Assertions.assertEquals("Jones", person.getLastName());
            Assertions.assertEquals(30, person.getAge());
        }
    }

    /**
     * Test JsonSchema SerDe with trust-all enabled (development mode)
     */
    @Test
    public void testJsonSchemaSerdeWithTrustAll() throws Exception {
        InputStream jsonSchema = getClass()
                .getResourceAsStream("/io/apicurio/registry/util/json-schema.json");
        Assertions.assertNotNull(jsonSchema);

        String groupId = TestUtils.generateGroupId();
        String artifactId = generateArtifactId();

        createArtifact(groupId, artifactId, ArtifactType.JSON, IoUtil.toString(jsonSchema),
                ContentTypes.APPLICATION_JSON);

        Person person = new Person("Charlie", "Brown", 35);

        try (JsonSchemaKafkaSerializer<Person> serializer = new JsonSchemaKafkaSerializer<>();
            Deserializer<Person> deserializer = new JsonSchemaKafkaDeserializer<>()) {

            // Common SerDe configuration
            Map<String, Object> commonConfig = new HashMap<>();
            commonConfig.put(SerdeConfig.REGISTRY_URL, registryV3ApiUrl);
            // TLS configuration with trust-all enabled
            commonConfig.put(SchemaResolverConfig.TLS_TRUST_ALL, "true");

            // Configure the serializer
            Map<String, Object> serializerConfig = new HashMap<>();
            serializerConfig.putAll(commonConfig);
            serializerConfig.put(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId);
            serializerConfig.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, SimpleTopicIdStrategy.class.getName());
            serializerConfig.put(KafkaSerdeConfig.ENABLE_HEADERS, "true");
            serializerConfig.put(SerdeConfig.VALIDATION_ENABLED, "true");
            serializer.configure(serializerConfig, false);

            // Configure the deserializer
            Map<String, Object> deserializerConfig = new HashMap<>();
            deserializerConfig.putAll(commonConfig);
            deserializerConfig.put(KafkaSerdeConfig.ENABLE_HEADERS, "true");
            deserializer.configure(deserializerConfig, false);

            Headers headers = new RecordHeaders();
            byte[] bytes = serializer.serialize(artifactId, headers, person);

            person = deserializer.deserialize(artifactId, headers, bytes);

            Assertions.assertEquals("Charlie", person.getFirstName());
            Assertions.assertEquals("Brown", person.getLastName());
            Assertions.assertEquals(35, person.getAge());
        }
    }

    /**
     * Test that SerDe fails without TLS configuration when server uses HTTPS
     */
    @Test
    public void testJsonSchemaSerdeWithoutTlsConfigFails() throws Exception {
        InputStream jsonSchema = getClass()
                .getResourceAsStream("/io/apicurio/registry/util/json-schema.json");
        Assertions.assertNotNull(jsonSchema);

        String groupId = TestUtils.generateGroupId();
        String artifactId = generateArtifactId();

        createArtifact(groupId, artifactId, ArtifactType.JSON, IoUtil.toString(jsonSchema),
                ContentTypes.APPLICATION_JSON);

        Person person = new Person("Eve", "Adams", 40);

        try (JsonSchemaKafkaSerializer<Person> serializer = new JsonSchemaKafkaSerializer<>()) {
            // Common SerDe configuration
            Map<String, Object> commonConfig = new HashMap<>();
            commonConfig.put(SerdeConfig.REGISTRY_URL, registryV3ApiUrl);
            // No TLS configuration - should fail

            // Configure the serializer
            Map<String, Object> serializerConfig = new HashMap<>();
            serializerConfig.putAll(commonConfig);
            serializerConfig.put(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId);
            serializerConfig.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, SimpleTopicIdStrategy.class.getName());
            serializerConfig.put(KafkaSerdeConfig.ENABLE_HEADERS, "true");
            serializerConfig.put(SerdeConfig.VALIDATION_ENABLED, "true");
            serializer.configure(serializerConfig, false);

            Headers headers = new RecordHeaders();
            // This (serialize) should fail with an SSL-related error
            Exception exception = Assertions.assertThrows(Exception.class, () -> {
                serializer.serialize(artifactId, headers, person);
            });

            // Verify it's an SSL-related error
            boolean isSslError = isSslException(exception);
            if (!isSslError) {
                Assertions.fail("Expected SSL-related error, but got something else.", exception);
            }
        }
    }

    /**
     * Helper method to check if the exception thrown was an SSL related error.
     */
    private boolean isSslException(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) {
            if (cause instanceof SSLHandshakeException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}