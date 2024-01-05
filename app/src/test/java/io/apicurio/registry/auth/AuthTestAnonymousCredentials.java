package io.apicurio.registry.auth;

import com.microsoft.kiota.ApiException;
import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.AuthTestProfileAnonymousCredentials;
import io.apicurio.registry.utils.tests.JWKSMockServer;
import io.kiota.http.vertx.VertXRequestAdapter;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.apicurio.registry.client.auth.VertXAuthFactory;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.apicurio.registry.client.auth.VertXAuthFactory.buildOIDCWebClient;

@QuarkusTest
@TestProfile(AuthTestProfileAnonymousCredentials.class)
@Tag(ApicurioTestTags.SLOW)
public class AuthTestAnonymousCredentials extends AbstractResourceTestBase {

    @ConfigProperty(name = "registry.auth.token.endpoint")
    @Info(category = "auth", description = "Auth token endpoint", availableSince = "2.1.0.Final")
    String authServerUrl;

    final String groupId = getClass().getSimpleName() + "Group";

    @Test
    public void testWrongCreds() throws Exception {
        var adapter = new VertXRequestAdapter(buildOIDCWebClient(authServerUrl, JWKSMockServer.WRONG_CREDS_CLIENT_ID, "secret"));
        adapter.setBaseUrl(registryV3ApiUrl);
        RegistryClient client = new RegistryClient(adapter);
        assertNotAuthorized(Assertions.assertThrows(Exception.class, () -> {
            client.groups().byGroupId(groupId).artifacts().get();
        }));
    }

    @Test
    public void testNoCredentials() throws Exception {
        var adapter = new VertXRequestAdapter(VertXAuthFactory.defaultVertx);
        adapter.setBaseUrl(registryV3ApiUrl);
        RegistryClient client = new RegistryClient(adapter);
        // Read-only operation should work without any credentials.
        var results = client.search().artifacts().get(config -> config.queryParameters.group = groupId);
        Assertions.assertTrue(results.getCount() >= 0);

        // Write operation should fail without any credentials
        String data = "{\r\n" +
                "    \"type\" : \"record\",\r\n" +
                "    \"name\" : \"userInfo\",\r\n" +
                "    \"namespace\" : \"my.example\",\r\n" +
                "    \"fields\" : [{\"name\" : \"age\", \"type\" : \"int\"}]\r\n" +
                "}";
        var exception = Assertions.assertThrows(ApiException.class, () -> {
            var content = new io.apicurio.registry.rest.client.models.ArtifactContent();
            content.setContent(data);
            client
                .groups()
                .byGroupId(groupId)
                .artifacts()
                .post(content, config -> {
                    config.headers.add("X-Registry-ArtifactType", ArtifactType.AVRO);
                    config.headers.add("X-Registry-ArtifactId", "testNoCredentials");
                });
        });
        Assertions.assertEquals(401, exception.getResponseStatusCode());
    }
}
