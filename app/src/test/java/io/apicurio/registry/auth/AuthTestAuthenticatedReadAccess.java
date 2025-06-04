package io.apicurio.registry.auth;

import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.client.auth.VertXAuthFactory;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.AuthTestProfileAuthenticatedReadAccess;
import io.apicurio.registry.utils.tests.KeycloakTestContainerManager;
import io.apicurio.registry.utils.tests.TestUtils;
import io.kiota.http.vertx.VertXRequestAdapter;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.vertx.core.Vertx;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.apicurio.common.apps.config.Info.CATEGORY_AUTH;

@QuarkusTest
@TestProfile(AuthTestProfileAuthenticatedReadAccess.class)
@Tag(ApicurioTestTags.SLOW)
public class AuthTestAuthenticatedReadAccess extends AbstractResourceTestBase {

    @ConfigProperty(name = "quarkus.oidc.token-path")
    @Info(category = CATEGORY_AUTH, description = "Auth token endpoint", availableSince = "2.1.0.Final")
    String authServerUrl;

    final String groupId = getClass().getSimpleName() + "Group";

    @Override
    protected RegistryClient createRestClientV3(Vertx vertx) {
        var adapter = new VertXRequestAdapter(VertXAuthFactory.buildOIDCWebClient(vertx, authServerUrl,
                KeycloakTestContainerManager.ADMIN_CLIENT_ID, "test1"));
        adapter.setBaseUrl(registryV3ApiUrl);
        return new RegistryClient(adapter);
    }

    @Test
    public void testReadOperationWithNoRole() throws Exception {
        // Read-only operation should work with credentials but no role.
        var adapter = new VertXRequestAdapter(VertXAuthFactory.buildOIDCWebClient(vertx, authServerUrl,
                KeycloakTestContainerManager.NO_ROLE_CLIENT_ID, "test1"));
        adapter.setBaseUrl(registryV3ApiUrl);
        RegistryClient client = new RegistryClient(adapter);
        var results = client.search().artifacts().get(config -> config.queryParameters.groupId = groupId);
        Assertions.assertTrue(results.getCount() >= 0);

        // Write operation should fail with credentials but not role.
        String data = "{\r\n" + "    \"type\" : \"record\",\r\n" + "    \"name\" : \"userInfo\",\r\n"
                + "    \"namespace\" : \"my.example\",\r\n"
                + "    \"fields\" : [{\"name\" : \"age\", \"type\" : \"int\"}]\r\n" + "}";
        var exception = Assertions.assertThrows(Exception.class, () -> {
            CreateArtifact createArtifact = TestUtils.clientCreateArtifact("testReadOperationWithNoRole",
                    ArtifactType.AVRO, data, ContentTypes.APPLICATION_JSON);
            client.groups().byGroupId(groupId).artifacts().post(createArtifact);
        });
        assertForbidden(exception);
    }
}
