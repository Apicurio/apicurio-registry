package io.apicurio.registry.auth;

import com.microsoft.kiota.ApiException;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.AuthTestProfileAnonymousCredentials;
import io.apicurio.registry.utils.tests.KeycloakTestContainerManager;
import io.apicurio.registry.utils.tests.TestUtils;
import io.kiota.http.vertx.VertXRequestAdapter;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.apicurio.registry.client.auth.VertXAuthFactory.buildOIDCWebClient;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(AuthTestProfileAnonymousCredentials.class)
@Tag(ApicurioTestTags.SLOW)
public class AuthTestAnonymousCredentials extends AbstractResourceTestBase {

    @ConfigProperty(name = "quarkus.oidc.token-path")
    String authServerUrl;

    final String groupId = getClass().getSimpleName() + "Group";

    @BeforeEach
    protected void beforeEach() throws Exception {
        setupRestAssured();
    }

    @Test
    public void testWrongCreds() throws Exception {
        var adapter = new VertXRequestAdapter(buildOIDCWebClient(vertx, authServerUrl,
                KeycloakTestContainerManager.DEVELOPER_CLIENT_ID, "test55"));
        adapter.setBaseUrl(registryV3ApiUrl);
        RegistryClient client = new RegistryClient(adapter);

        var exception = Assertions.assertThrows(Exception.class, () -> {
            client.groups().byGroupId(groupId).artifacts().get();
        });

        assertTrue(exception.getMessage().contains("unauthorized"));
    }

    @Test
    public void testNoCredentials() throws Exception {
        var adapter = new VertXRequestAdapter(vertx);
        adapter.setBaseUrl(registryV3ApiUrl);
        RegistryClient client = new RegistryClient(adapter);
        // Read-only operation should work without any credentials.
        var results = client.search().artifacts().get(config -> config.queryParameters.groupId = groupId);
        Assertions.assertTrue(results.getCount() >= 0);

        // Write operation should fail without any credentials
        String data = "{\r\n" + "    \"type\" : \"record\",\r\n" + "    \"name\" : \"userInfo\",\r\n"
                + "    \"namespace\" : \"my.example\",\r\n"
                + "    \"fields\" : [{\"name\" : \"age\", \"type\" : \"int\"}]\r\n" + "}";
        var exception = Assertions.assertThrows(ApiException.class, () -> {
            CreateArtifact createArtifact = TestUtils.clientCreateArtifact("testNoCredentials",
                    ArtifactType.AVRO, data, ContentTypes.APPLICATION_JSON);
            client.groups().byGroupId(groupId).artifacts().post(createArtifact);
        });
        Assertions.assertEquals(401, exception.getResponseStatusCode());
    }
}
