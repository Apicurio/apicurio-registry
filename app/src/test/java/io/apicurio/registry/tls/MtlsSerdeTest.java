package io.apicurio.registry.tls;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.resolver.config.SchemaResolverConfig;
import io.apicurio.registry.serde.config.SerdeConfig;
import io.apicurio.registry.serde.kafka.config.KafkaSerdeConfig;
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

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Tests that SerDe classes (serializers/deserializers) work with mutual TLS (mTLS)
 * configuration using JKS, PKCS12, and PEM keystores when connecting to a registry
 * that requires client certificate authentication.
 */
@QuarkusTest
@TestProfile(MtlsSerdeTest.MtlsSerdeTestProfile.class)
public class MtlsSerdeTest extends AbstractResourceTestBase {

    public static class MtlsSerdeTestProfile extends MtlsTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            Map<String, String> props = new HashMap<>(super.getConfigOverrides());
            // Keep basic auth enabled so that Quarkus registers the BasicAuthenticationMechanism bean.
            // Note: Both basic auth (HTTP application layer) and mTLS client certification (TCP/TLS transport layer)
            // coexist. The Quarkus HTTP server requires a valid client certificate to complete the TLS handshake;
            // hence, the negative test (connecting without client keystore) fails at the transport layer before
            // HTTP basic auth is evaluated.
            props.put("quarkus.http.auth.basic", "true");
            // Define an embedded user for our tests to authenticate with basic auth
            props.put("quarkus.security.users.embedded.enabled", "true");
            props.put("quarkus.security.users.embedded.plain-text", "true");
            props.put("quarkus.security.users.embedded.users.alice", "alice");
            props.put("quarkus.security.users.embedded.roles.alice", "sr-admin");
            return props;
        }
    }

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

        URL truststoreUrl = getClass().getClassLoader().getResource("tls/registry-truststore.jks");
        URL keystoreUrl = getClass().getClassLoader().getResource("tls/client-keystore.jks");
        Assertions.assertNotNull(truststoreUrl, "Truststore file not found");
        Assertions.assertNotNull(keystoreUrl, "Client keystore file not found");

        // Initialize API client with mTLS and Basic Auth
        clientV3 = RegistryClientFactory.create(RegistryClientOptions.create()
                .registryUrl(registryV3ApiUrl)
                .trustStoreJks(truststoreUrl.getPath(), "registrytest")
                .keystoreJks(keystoreUrl.getPath(), "registrytest")
                .basicAuth("alice", "alice")
                .vertx(vertx));
    }

    /**
     * Helper to run basic serialization/deserialization flow with a specific SerDe configuration.
     */
    private void runSerdeTest(Map<String, Object> commonConfig, Person person) throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = generateArtifactId();

        InputStream jsonSchema = getClass()
                .getResourceAsStream("/io/apicurio/registry/util/json-schema.json");
        Assertions.assertNotNull(jsonSchema);

        createArtifact(groupId, artifactId, ArtifactType.JSON, IoUtil.toString(jsonSchema),
                ContentTypes.APPLICATION_JSON);

        try (JsonSchemaKafkaSerializer<Person> serializer = new JsonSchemaKafkaSerializer<>();
            Deserializer<Person> deserializer = new JsonSchemaKafkaDeserializer<>()) {

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
            Person deserialized = deserializer.deserialize(artifactId, headers, bytes);

            Assertions.assertEquals(person.getFirstName(), deserialized.getFirstName());
            Assertions.assertEquals(person.getLastName(), deserialized.getLastName());
            Assertions.assertEquals(person.getAge(), deserialized.getAge());
        }
    }

    /**
     * Test JsonSchema SerDe with JKS truststore and JKS keystore (mTLS)
     */
    @Test
    public void testJsonSchemaSerdeWithJksKeystore() throws Exception {
        URL truststoreUrl = getClass().getClassLoader().getResource("tls/registry-truststore.jks");
        URL keystoreUrl = getClass().getClassLoader().getResource("tls/client-keystore.jks");
        Assertions.assertNotNull(truststoreUrl);
        Assertions.assertNotNull(keystoreUrl);

        Map<String, Object> config = new HashMap<>();
        config.put(SerdeConfig.REGISTRY_URL, registryV3ApiUrl);
        config.put(SchemaResolverConfig.AUTH_USERNAME, "alice");
        config.put(SchemaResolverConfig.AUTH_PASSWORD, "alice");

        config.put(SchemaResolverConfig.TLS_TRUSTSTORE_LOCATION, truststoreUrl.getPath());
        config.put(SchemaResolverConfig.TLS_TRUSTSTORE_PASSWORD, "registrytest");
        config.put(SchemaResolverConfig.TLS_TRUSTSTORE_TYPE, "JKS");

        config.put(SchemaResolverConfig.TLS_KEYSTORE_LOCATION, keystoreUrl.getPath());
        config.put(SchemaResolverConfig.TLS_KEYSTORE_PASSWORD, "registrytest");
        config.put(SchemaResolverConfig.TLS_KEYSTORE_TYPE, "JKS");

        runSerdeTest(config, new Person("Bob", "Johnson", 40));
    }

    /**
     * Test JsonSchema SerDe with PKCS12 truststore and PKCS12 keystore (mTLS)
     */
    @Test
    public void testJsonSchemaSerdeWithPkcs12Keystore() throws Exception {
        URL truststoreUrl = getClass().getClassLoader().getResource("tls/registry-truststore.p12");
        URL keystoreUrl = getClass().getClassLoader().getResource("tls/client-keystore.p12");
        Assertions.assertNotNull(truststoreUrl);
        Assertions.assertNotNull(keystoreUrl);

        Map<String, Object> config = new HashMap<>();
        config.put(SerdeConfig.REGISTRY_URL, registryV3ApiUrl);
        config.put(SchemaResolverConfig.AUTH_USERNAME, "alice");
        config.put(SchemaResolverConfig.AUTH_PASSWORD, "alice");

        config.put(SchemaResolverConfig.TLS_TRUSTSTORE_LOCATION, truststoreUrl.getPath());
        config.put(SchemaResolverConfig.TLS_TRUSTSTORE_PASSWORD, "registrytest");
        config.put(SchemaResolverConfig.TLS_TRUSTSTORE_TYPE, "PKCS12");

        config.put(SchemaResolverConfig.TLS_KEYSTORE_LOCATION, keystoreUrl.getPath());
        config.put(SchemaResolverConfig.TLS_KEYSTORE_PASSWORD, "registrytest");
        config.put(SchemaResolverConfig.TLS_KEYSTORE_TYPE, "PKCS12");

        runSerdeTest(config, new Person("Alice", "Smith", 25));
    }

    /**
     * Test JsonSchema SerDe with PEM keystore via location, type, and key path
     */
    @Test
    public void testJsonSchemaSerdeWithPemKeystoreLocation() throws Exception {
        URL truststoreUrl = getClass().getClassLoader().getResource("tls/registry-cert.pem");
        URL clientCertUrl = getClass().getClassLoader().getResource("tls/client-cert.pem");
        URL clientKeyUrl = getClass().getClassLoader().getResource("tls/client-key.pem");
        Assertions.assertNotNull(truststoreUrl);
        Assertions.assertNotNull(clientCertUrl);
        Assertions.assertNotNull(clientKeyUrl);

        Map<String, Object> config = new HashMap<>();
        config.put(SerdeConfig.REGISTRY_URL, registryV3ApiUrl);
        config.put(SchemaResolverConfig.AUTH_USERNAME, "alice");
        config.put(SchemaResolverConfig.AUTH_PASSWORD, "alice");

        config.put(SchemaResolverConfig.TLS_TRUSTSTORE_LOCATION, truststoreUrl.getPath());
        config.put(SchemaResolverConfig.TLS_TRUSTSTORE_TYPE, "PEM");

        config.put(SchemaResolverConfig.TLS_KEYSTORE_LOCATION, clientCertUrl.getPath());
        config.put(SchemaResolverConfig.TLS_KEYSTORE_TYPE, "PEM");
        config.put(SchemaResolverConfig.TLS_CLIENT_KEY, clientKeyUrl.getPath());

        runSerdeTest(config, new Person("Emma", "Wilson", 32));
    }

    /**
     * Test JsonSchema SerDe with PEM keystore via raw cert and key content
     */
    @Test
    public void testJsonSchemaSerdeWithPemKeystoreContent() throws Exception {
        URL truststoreUrl = getClass().getClassLoader().getResource("tls/registry-cert.pem");
        URL clientCertUrl = getClass().getClassLoader().getResource("tls/client-cert.pem");
        URL clientKeyUrl = getClass().getClassLoader().getResource("tls/client-key.pem");
        Assertions.assertNotNull(truststoreUrl);
        Assertions.assertNotNull(clientCertUrl);
        Assertions.assertNotNull(clientKeyUrl);

        String clientCertContent = Files.readString(Paths.get(clientCertUrl.getPath()));
        String clientKeyContent = Files.readString(Paths.get(clientKeyUrl.getPath()));

        Map<String, Object> config = new HashMap<>();
        config.put(SerdeConfig.REGISTRY_URL, registryV3ApiUrl);
        config.put(SchemaResolverConfig.AUTH_USERNAME, "alice");
        config.put(SchemaResolverConfig.AUTH_PASSWORD, "alice");

        config.put(SchemaResolverConfig.TLS_TRUSTSTORE_LOCATION, truststoreUrl.getPath());
        config.put(SchemaResolverConfig.TLS_TRUSTSTORE_TYPE, "PEM");

        config.put(SchemaResolverConfig.TLS_CLIENT_CERTIFICATE, clientCertContent);
        config.put(SchemaResolverConfig.TLS_CLIENT_KEY, clientKeyContent);

        runSerdeTest(config, new Person("John", "Doe", 45));
    }

    /**
     * Test JsonSchema SerDe with mixed PEM: cert as file path and key as inline content
     */
    @Test
    public void testJsonSchemaSerdeWithPemKeystoreMixedCertPathKeyContent() throws Exception {
        URL truststoreUrl = getClass().getClassLoader().getResource("tls/registry-cert.pem");
        URL clientCertUrl = getClass().getClassLoader().getResource("tls/client-cert.pem");
        URL clientKeyUrl = getClass().getClassLoader().getResource("tls/client-key.pem");
        Assertions.assertNotNull(truststoreUrl);
        Assertions.assertNotNull(clientCertUrl);
        Assertions.assertNotNull(clientKeyUrl);

        String clientKeyContent = Files.readString(Paths.get(clientKeyUrl.getPath()));

        Map<String, Object> config = new HashMap<>();
        config.put(SerdeConfig.REGISTRY_URL, registryV3ApiUrl);
        config.put(SchemaResolverConfig.AUTH_USERNAME, "alice");
        config.put(SchemaResolverConfig.AUTH_PASSWORD, "alice");

        config.put(SchemaResolverConfig.TLS_TRUSTSTORE_LOCATION, truststoreUrl.getPath());
        config.put(SchemaResolverConfig.TLS_TRUSTSTORE_TYPE, "PEM");

        // Cert is file path, Key is inline content
        config.put(SchemaResolverConfig.TLS_CLIENT_CERTIFICATE, clientCertUrl.getPath());
        config.put(SchemaResolverConfig.TLS_CLIENT_KEY, clientKeyContent);

        runSerdeTest(config, new Person("Jane", "Doe", 42));
    }

    /**
     * Test JsonSchema SerDe with mixed PEM: cert as inline content and key as file path
     */
    @Test
    public void testJsonSchemaSerdeWithPemKeystoreMixedCertContentKeyPath() throws Exception {
        URL truststoreUrl = getClass().getClassLoader().getResource("tls/registry-cert.pem");
        URL clientCertUrl = getClass().getClassLoader().getResource("tls/client-cert.pem");
        URL clientKeyUrl = getClass().getClassLoader().getResource("tls/client-key.pem");
        Assertions.assertNotNull(truststoreUrl);
        Assertions.assertNotNull(clientCertUrl);
        Assertions.assertNotNull(clientKeyUrl);

        String clientCertContent = Files.readString(Paths.get(clientCertUrl.getPath()));

        Map<String, Object> config = new HashMap<>();
        config.put(SerdeConfig.REGISTRY_URL, registryV3ApiUrl);
        config.put(SchemaResolverConfig.AUTH_USERNAME, "alice");
        config.put(SchemaResolverConfig.AUTH_PASSWORD, "alice");

        config.put(SchemaResolverConfig.TLS_TRUSTSTORE_LOCATION, truststoreUrl.getPath());
        config.put(SchemaResolverConfig.TLS_TRUSTSTORE_TYPE, "PEM");

        // Cert is inline content, Key is file path
        config.put(SchemaResolverConfig.TLS_CLIENT_CERTIFICATE, clientCertContent);
        config.put(SchemaResolverConfig.TLS_CLIENT_KEY, clientKeyUrl.getPath());

        runSerdeTest(config, new Person("Grace", "Hopper", 85));
    }

    /**
     * Negative test: verify that connecting without a client keystore fails when mTLS is required.
     */
    @Test
    public void testJsonSchemaSerdeWithoutClientKeystoreFails() throws Exception {
        URL truststoreUrl = getClass().getClassLoader().getResource("tls/registry-truststore.jks");
        Assertions.assertNotNull(truststoreUrl);

        InputStream jsonSchema = getClass()
                .getResourceAsStream("/io/apicurio/registry/util/json-schema.json");
        Assertions.assertNotNull(jsonSchema);

        String groupId = TestUtils.generateGroupId();
        String artifactId = generateArtifactId();

        // Registering artifact via clientV3 succeeds because clientV3 uses valid mTLS credentials
        createArtifact(groupId, artifactId, ArtifactType.JSON, IoUtil.toString(jsonSchema),
                ContentTypes.APPLICATION_JSON);

        Person person = new Person("Charlie", "Brown", 12);

        try (JsonSchemaKafkaSerializer<Person> serializer = new JsonSchemaKafkaSerializer<>()) {
            Map<String, Object> config = new HashMap<>();
            config.put(SerdeConfig.REGISTRY_URL, registryV3ApiUrl);
            config.put(SchemaResolverConfig.AUTH_USERNAME, "alice");
            config.put(SchemaResolverConfig.AUTH_PASSWORD, "alice");
            config.put(SchemaResolverConfig.TLS_TRUSTSTORE_LOCATION, truststoreUrl.getPath());
            config.put(SchemaResolverConfig.TLS_TRUSTSTORE_PASSWORD, "registrytest");
            config.put(SchemaResolverConfig.TLS_TRUSTSTORE_TYPE, "JKS");

            config.put(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId);
            config.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, SimpleTopicIdStrategy.class.getName());

            // Should throw an exception during configuration or serialization because mTLS is missing
            serializer.configure(config, false);
            Headers headers = new RecordHeaders();
            Assertions.assertThrows(Exception.class, () -> serializer.serialize(artifactId, headers, person));
        }
    }
}
