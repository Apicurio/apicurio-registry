package io.apicurio.registry.tls;

import com.microsoft.kiota.ApiException;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.resolver.config.SchemaResolverConfig;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.SystemInfo;
import io.apicurio.registry.serde.config.SerdeConfig;
import io.apicurio.registry.serde.kafka.config.KafkaSerdeConfig;
import io.apicurio.registry.serde.jsonschema.JsonSchemaKafkaDeserializer;
import io.apicurio.registry.serde.jsonschema.JsonSchemaKafkaSerializer;
import io.apicurio.registry.serde.strategy.SimpleTopicIdStrategy;
import io.apicurio.registry.support.Person;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.KeycloakTestContainerManager;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.vertx.core.Vertx;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.Deserializer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLHandshakeException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Tests that SerDe classes (serializers/deserializers) work with both TLS/SSL configuration
 * AND authentication when connecting to an HTTPS-enabled Registry that requires authentication
 * via an HTTPS-enabled Keycloak server.
 *
 * This test verifies that the TLS configuration applies to BOTH:
 * 1. Registry API calls (artifact registration, schema resolution)
 * 2. Keycloak authentication calls (token acquisition)
 *
 * The test uses a combined truststore containing certificates for both Registry and Keycloak.
 */
@QuarkusTest
@TestProfile(TlsAuthTestProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class TlsAuthSerdeTest extends AbstractResourceTestBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(TlsAuthSerdeTest.class);

    @TestHTTPResource(value = "/apis", tls = true)
    URL httpsUrl;

    @ConfigProperty(name = "quarkus.oidc.token-path")
    String oidcTokenPath;

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

        // Create client with both TLS (trust all for test setup) and OAuth2
        clientV3 = RegistryClientFactory.create(RegistryClientOptions.create()
                .registryUrl(registryV3ApiUrl)
                .trustAll(true)
                .oauth2(oidcTokenPath, KeycloakTestContainerManager.ADMIN_CLIENT_ID, "test1")
                .vertx(vertx));
    }

    /**
     * Sanity check test: Verify Registry HTTPS connection works with combined truststore
     * but WITHOUT authentication (uses public/unauthenticated endpoint).
     * This isolates TLS configuration from authentication concerns.
     */
    @Test
    public void testRegistryHttpsConnectionWithTruststore() {
        URL truststoreUrl = getClass().getClassLoader().getResource("tls/combined-truststore.jks");
        Assertions.assertNotNull(truststoreUrl, "Combined truststore file not found");

        LOGGER.info("Testing Registry HTTPS connection with truststore (no auth)");
        LOGGER.info("Registry URL: {}", registryV3ApiUrl);
        LOGGER.info("Truststore: {}", truststoreUrl.getPath());

        // Create a client with TLS but NO authentication
        RegistryClient client = RegistryClientFactory.create(RegistryClientOptions.create()
                .registryUrl(registryV3ApiUrl)
                .trustStoreJks(truststoreUrl.getPath(), "registrytest")
                .vertx(vertx));

        // Call the system info endpoint (public, no auth required)
        SystemInfo systemInfo = client.system().info().get();

        Assertions.assertNotNull(systemInfo, "System info should not be null");
        LOGGER.info("Successfully retrieved system info over HTTPS");
        LOGGER.info("Registry name: {}", systemInfo.getName());
        LOGGER.info("Registry version: {}", systemInfo.getVersion());

        // Call the system info endpoint (public, no auth required)
        systemInfo = clientV3.system().info().get();

        Assertions.assertNotNull(systemInfo, "System info should not be null");
        LOGGER.info("Successfully retrieved system info over HTTPS");
        LOGGER.info("Registry name: {}", systemInfo.getName());
        LOGGER.info("Registry version: {}", systemInfo.getVersion());

        // Should NOT be able to call the admin rules endpoints for "client" - no auth credentials
        Assertions.assertThrows(ApiException.class, () -> {
            client.admin().rules().get();
        });

        // Should be able to call the admin rules endpoints (clientV3 has admin user configured).
        clientV3.admin().rules().get();
    }

    /**
     * Test JsonSchema SerDe with combined JKS truststore (Registry + Keycloak) and OAuth2 authentication.
     * This is the primary positive test case.
     */
    @Test
    public void testJsonSchemaSerdeWithCombinedTruststoreAndAuth() throws Exception {
        URL truststoreUrl = getClass().getClassLoader().getResource("tls/combined-truststore.jks");
        Assertions.assertNotNull(truststoreUrl, "Combined truststore file not found");

        InputStream jsonSchema = getClass()
                .getResourceAsStream("/io/apicurio/registry/util/json-schema.json");
        Assertions.assertNotNull(jsonSchema);

        String groupId = TestUtils.generateGroupId();
        String artifactId = generateArtifactId();

        createArtifact(groupId, artifactId, ArtifactType.JSON, IoUtil.toString(jsonSchema),
                ContentTypes.APPLICATION_JSON);

        Person person = new Person("Alice", "Anderson", 28);

        try (JsonSchemaKafkaSerializer<Person> serializer = new JsonSchemaKafkaSerializer<>();
             Deserializer<Person> deserializer = new JsonSchemaKafkaDeserializer<>()) {

            // Common SerDe configuration
            Map<String, Object> commonConfig = new HashMap<>();
            commonConfig.put(SerdeConfig.REGISTRY_URL, registryV3ApiUrl);

            // TLS configuration with combined truststore (Registry + Keycloak certificates)
            commonConfig.put(SchemaResolverConfig.TLS_TRUSTSTORE_LOCATION, truststoreUrl.getPath());
            commonConfig.put(SchemaResolverConfig.TLS_TRUSTSTORE_PASSWORD, "registrytest");
            commonConfig.put(SchemaResolverConfig.TLS_TRUSTSTORE_TYPE, "JKS");

            // OAuth2 authentication configuration (for Keycloak)
            commonConfig.put(SchemaResolverConfig.AUTH_TOKEN_ENDPOINT, oidcTokenPath);
            commonConfig.put(SchemaResolverConfig.AUTH_CLIENT_ID, KeycloakTestContainerManager.DEVELOPER_CLIENT_ID);
            commonConfig.put(SchemaResolverConfig.AUTH_CLIENT_SECRET, "test1");

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
            Assertions.assertEquals("Anderson", person.getLastName());
            Assertions.assertEquals(28, person.getAge());
        }
    }

    /**
     * Test JsonSchema SerDe with combined PKCS12 truststore and OAuth2 authentication.
     */
    @Test
    public void testJsonSchemaSerdeWithCombinedPkcs12TruststoreAndAuth() throws Exception {
        URL truststoreUrl = getClass().getClassLoader().getResource("tls/combined-truststore.p12");
        Assertions.assertNotNull(truststoreUrl, "Combined PKCS12 truststore file not found");

        InputStream jsonSchema = getClass()
                .getResourceAsStream("/io/apicurio/registry/util/json-schema.json");
        Assertions.assertNotNull(jsonSchema);

        String groupId = TestUtils.generateGroupId();
        String artifactId = generateArtifactId();

        createArtifact(groupId, artifactId, ArtifactType.JSON, IoUtil.toString(jsonSchema),
                ContentTypes.APPLICATION_JSON);

        Person person = new Person("Bob", "Baker", 35);

        try (JsonSchemaKafkaSerializer<Person> serializer = new JsonSchemaKafkaSerializer<>();
             Deserializer<Person> deserializer = new JsonSchemaKafkaDeserializer<>()) {

            // Common SerDe configuration
            Map<String, Object> commonConfig = new HashMap<>();
            commonConfig.put(SerdeConfig.REGISTRY_URL, registryV3ApiUrl);

            // TLS configuration with combined PKCS12 truststore
            commonConfig.put(SchemaResolverConfig.TLS_TRUSTSTORE_LOCATION, truststoreUrl.getPath());
            commonConfig.put(SchemaResolverConfig.TLS_TRUSTSTORE_PASSWORD, "registrytest");
            commonConfig.put(SchemaResolverConfig.TLS_TRUSTSTORE_TYPE, "PKCS12");

            // OAuth2 authentication configuration
            commonConfig.put(SchemaResolverConfig.AUTH_TOKEN_ENDPOINT, oidcTokenPath);
            commonConfig.put(SchemaResolverConfig.AUTH_CLIENT_ID, KeycloakTestContainerManager.DEVELOPER_CLIENT_ID);
            commonConfig.put(SchemaResolverConfig.AUTH_CLIENT_SECRET, "test1");

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

            Assertions.assertEquals("Bob", person.getFirstName());
            Assertions.assertEquals("Baker", person.getLastName());
            Assertions.assertEquals(35, person.getAge());
        }
    }

    /**
     * Test that SerDe fails when using only Registry truststore (missing Keycloak certificate).
     * This demonstrates that BOTH certificates are required.
     */
    @Test
    public void testJsonSchemaSerdeWithOnlyRegistryTruststoreFails() throws Exception {
        // Use the Registry-only truststore (does NOT contain Keycloak certificate)
        URL truststoreUrl = getClass().getClassLoader().getResource("tls/registry-truststore.jks");
        Assertions.assertNotNull(truststoreUrl, "Registry truststore file not found");

        InputStream jsonSchema = getClass()
                .getResourceAsStream("/io/apicurio/registry/util/json-schema.json");
        Assertions.assertNotNull(jsonSchema);

        String groupId = TestUtils.generateGroupId();
        String artifactId = generateArtifactId();

        createArtifact(groupId, artifactId, ArtifactType.JSON, IoUtil.toString(jsonSchema),
                ContentTypes.APPLICATION_JSON);

        Person person = new Person("Charlie", "Clark", 40);

        try (JsonSchemaKafkaSerializer<Person> serializer = new JsonSchemaKafkaSerializer<>()) {
            // Common SerDe configuration
            Map<String, Object> commonConfig = new HashMap<>();
            commonConfig.put(SerdeConfig.REGISTRY_URL, registryV3ApiUrl);

            // TLS configuration with ONLY Registry truststore (missing Keycloak cert)
            commonConfig.put(SchemaResolverConfig.TLS_TRUSTSTORE_LOCATION, truststoreUrl.getPath());
            commonConfig.put(SchemaResolverConfig.TLS_TRUSTSTORE_PASSWORD, "registrytest");
            commonConfig.put(SchemaResolverConfig.TLS_TRUSTSTORE_TYPE, "JKS");

            // OAuth2 authentication configuration
            commonConfig.put(SchemaResolverConfig.AUTH_TOKEN_ENDPOINT, oidcTokenPath);
            commonConfig.put(SchemaResolverConfig.AUTH_CLIENT_ID, KeycloakTestContainerManager.DEVELOPER_CLIENT_ID);
            commonConfig.put(SchemaResolverConfig.AUTH_CLIENT_SECRET, "test1");

            // Configure the serializer
            Map<String, Object> serializerConfig = new HashMap<>();
            serializerConfig.putAll(commonConfig);
            serializerConfig.put(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId);
            serializerConfig.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, SimpleTopicIdStrategy.class.getName());
            serializerConfig.put(KafkaSerdeConfig.ENABLE_HEADERS, "true");
            serializerConfig.put(SerdeConfig.VALIDATION_ENABLED, "true");
            serializer.configure(serializerConfig, false);

            Headers headers = new RecordHeaders();
            // This should fail because Keycloak certificate is not trusted
            Exception exception = Assertions.assertThrows(Exception.class, () -> {
                serializer.serialize(artifactId, headers, person);
            });

            // Verify it's an SSL-related error
            boolean isSslError = isSslException(exception);
            if (!isSslError) {
                Assertions.fail("Expected SSL-related error due to missing Keycloak certificate in truststore, but got something else.", exception);
            }
        }
    }

    /**
     * Helper method to check if the exception thrown was an SSL related error.
     */
    private boolean isSslException(Throwable throwable) {
        Throwable cause = throwable;
        while (cause != null && cause.getCause() != cause) {
            if (cause instanceof SSLHandshakeException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
