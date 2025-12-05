package io.apicurio.registry.auth;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.headers.Headers;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.AuthTestProfile;
import io.apicurio.registry.utils.tests.KeycloakTestContainerManager;
import io.apicurio.registry.utils.tests.TestUtils;
import io.confluent.kafka.schemaregistry.ParsedSchema;
import io.confluent.kafka.schemaregistry.SchemaProvider;
import io.confluent.kafka.schemaregistry.avro.AvroSchemaProvider;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.RestService;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import io.confluent.kafka.schemaregistry.json.JsonSchemaProvider;
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchemaProvider;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.vertx.core.Vertx;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.OAuth2FlowType;
import io.vertx.ext.auth.oauth2.OAuth2Options;
import io.vertx.ext.auth.oauth2.Oauth2Credentials;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@QuarkusTest
@TestProfile(AuthTestProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class CCompatAuthTest extends AbstractResourceTestBase {

    private final String AVRO_SCHEMA = "{\"type\":\"record\",\"name\":\"TestRecord\",\"fields\":[{\"name\":\"name\",\"type\":\"string\"}]}";
    private final String AVRO_SCHEMA_V2 = "{\"type\":\"record\",\"name\":\"TestRecord\",\"fields\":[{\"name\":\"name\",\"type\":\"string\",\"description\":\"string\"}]}";

    @ConfigProperty(name = "quarkus.oidc.token-path")
    String authServerUrlConfigured;

    /**
     * Override to create a client with admin auth creds.
     */
    @Override
    protected RegistryClient createRestClientV3(Vertx vertx) {
        return RegistryClientFactory.create(RegistryClientOptions.create(registryV3ApiUrl, vertx)
                .oauth2(authServerUrlConfigured, KeycloakTestContainerManager.ADMIN_CLIENT_ID, "test1"));
    }

    /**
     * Builds a confluent schema registry client.  Use the given auth token and put it into the
     * Authorization HTTP header (to make authenticated calls to /ccompat).
     * @param authToken
     */
    public SchemaRegistryClient buildClient(String authToken) {
        final List<SchemaProvider> schemaProviders = Arrays.asList(new JsonSchemaProvider(),
                new AvroSchemaProvider(), new ProtobufSchemaProvider());
        RestService restService = new RestService("http://localhost:" + testPort + "/apis/ccompat/v7");
        Map<String, String> headers = Map.of(
                Headers.GROUP_ID, "confluentV7-test-group",
                "Authorization", "Bearer " + authToken
        );
        return new CachedSchemaRegistryClient(
                restService, 3, schemaProviders,
                null, headers);
    }

    /**
     * Acquires an authentication token using OIDC Client Credentials flow.
     *
     * @param clientId the client ID for authentication
     * @param clientSecret the client secret for authentication
     * @return the access token
     * @throws Exception if token acquisition fails
     */
    public String acquireAuthToken(String clientId, String clientSecret) throws Exception {
        OAuth2Options oauth2Options = new OAuth2Options()
                .setFlow(OAuth2FlowType.CLIENT)
                .setClientId(clientId)
                .setClientSecret(clientSecret)
                .setTokenPath(authServerUrlConfigured);

        OAuth2Auth oauth2Auth = OAuth2Auth.create(vertx, oauth2Options);

        Oauth2Credentials credentials = new Oauth2Credentials();

        CompletableFuture<String> tokenFuture = new CompletableFuture<>();

        oauth2Auth.authenticate(credentials)
                .onSuccess(user -> {
                    String accessToken = user.principal().getString("access_token");
                    tokenFuture.complete(accessToken);
                })
                .onFailure(tokenFuture::completeExceptionally);

        return tokenFuture.get();
    }

    @Test
    public void testReadOnly() throws Exception {
        String readOnlyToken = acquireAuthToken(KeycloakTestContainerManager.READONLY_CLIENT_ID, "test1");
        try (var schemaRegistryClient = buildClient(readOnlyToken)) {
            // Get all subjects should work
            schemaRegistryClient.getAllSubjects();

            // Try to create a subject (should fail - read only)
            Assertions.assertThrows(Exception.class, () -> {
                String subject = TestUtils.generateSubject();
                ParsedSchema schema = new AvroSchemaProvider().parseSchema(AVRO_SCHEMA, null, false)
                    .orElseThrow(() -> new RuntimeException("Failed to parse Avro schema"));
                schemaRegistryClient.register(subject, schema);
            });
        }
    }

    @Test
    public void testOwnerOnlyAuthorization() throws Exception {
        String devToken = acquireAuthToken(KeycloakTestContainerManager.DEVELOPER_CLIENT_ID, "test1");
        String adminToken = acquireAuthToken(KeycloakTestContainerManager.ADMIN_CLIENT_ID, "test1");
        ParsedSchema avroSchema_v1 = new AvroSchemaProvider().parseSchema(AVRO_SCHEMA, null, false)
                .orElseThrow(() -> new RuntimeException("Failed to parse Avro schema"));
        ParsedSchema avroSchema_v2 = new AvroSchemaProvider().parseSchema(AVRO_SCHEMA_V2, null, false)
                .orElseThrow(() -> new RuntimeException("Failed to parse Avro schema"));

        try (var adminClient = buildClient(adminToken); var devClient = buildClient(devToken)) {
            // Admin user will create an artifact
            String subject1 = TestUtils.generateSubject();
            adminClient.register(subject1, avroSchema_v1);

            // Dev user cannot delete the subject because Dev user is not the owner
            RestClientException exception = Assertions.assertThrows(RestClientException.class, () -> {
                devClient.deleteSubject(subject1);
            });
            assertForbidden(exception);

            // But the admin user CAN delete the subject
            adminClient.deleteSubject(subject1);

            // Now the Dev user will create an artifact
            String subject2 = TestUtils.generateSubject();
            devClient.register(subject2, avroSchema_v1);

            // And the Admin user will delete it (allowed because it's the Admin user)
            adminClient.deleteSubject(subject2);

            // Admin user will create an artifact
            String subject3 = TestUtils.generateSubject();
            adminClient.register(subject3, avroSchema_v1);

            // Dev user cannot update with ifExists the same artifact because Dev user is not the owner
            exception = Assertions.assertThrows(RestClientException.class, () -> {
                devClient.register(subject3, avroSchema_v2);
            });
            assertForbidden(exception);
        }
    }

    @Test
    public void testRegisterWithReadPermission() throws Exception {
        String readOnlyToken = acquireAuthToken(KeycloakTestContainerManager.READONLY_CLIENT_ID, "test1");
        String adminToken = acquireAuthToken(KeycloakTestContainerManager.ADMIN_CLIENT_ID, "test1");
        ParsedSchema avroSchema = new AvroSchemaProvider().parseSchema(AVRO_SCHEMA, null, false)
                .orElseThrow(() -> new RuntimeException("Failed to parse Avro schema"));

        try (var adminClient = buildClient(adminToken); var readOnlyClient = buildClient(readOnlyToken)) {
            // Admin user creates a schema first
            String subject = TestUtils.generateSubject();
            int schemaId1 = adminClient.register(subject, avroSchema);

            // Read-only user should be able to "register" the same schema (which just looks it up)
            int schemaId2 = readOnlyClient.register(subject, avroSchema);

            // Both IDs should be the same since it's the same schema
            Assertions.assertEquals(schemaId1, schemaId2);

            // Read-only user should still NOT be able to create a new schema
            String newSubject = TestUtils.generateSubject();
            Assertions.assertThrows(Exception.class, () -> {
                readOnlyClient.register(newSubject, avroSchema);
            });
        }
    }

    @Test
    public void testAutoRegisterWithReadOnlyUser() throws Exception {
        String readOnlyToken = acquireAuthToken(KeycloakTestContainerManager.READONLY_CLIENT_ID, "test1");
        String adminToken = acquireAuthToken(KeycloakTestContainerManager.ADMIN_CLIENT_ID, "test1");
        ParsedSchema avroSchema = new AvroSchemaProvider().parseSchema(AVRO_SCHEMA, null, false)
                .orElseThrow(() -> new RuntimeException("Failed to parse Avro schema"));

        try (var adminClient = buildClient(adminToken); var readOnlyClient = buildClient(readOnlyToken)) {
            String subject = TestUtils.generateSubject();

            // Admin creates the schema
            adminClient.register(subject, avroSchema);

            // Read-only user can retrieve the schema by content (simulating auto.register.schemas lookup)
            int retrievedId = readOnlyClient.getId(subject, avroSchema);
            Assertions.assertTrue(retrievedId > 0);

            // Read-only user can also call register on existing schema (should succeed with READ permission)
            int registeredId = readOnlyClient.register(subject, avroSchema);
            Assertions.assertEquals(retrievedId, registeredId);
        }
    }

}
