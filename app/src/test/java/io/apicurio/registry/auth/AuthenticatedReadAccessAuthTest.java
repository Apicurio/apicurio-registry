package io.apicurio.registry.auth;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.MockOAuth2AuthTestProfile;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.vertx.core.Vertx;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(MockOAuth2AuthTestProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class AuthenticatedReadAccessAuthTest extends AbstractResourceTestBase {

    /**
     * Client ids are arbitrary for the mock OIDC server - any credentials are accepted,
     * but issued tokens carry no roles unless claims are added explicitly.
     */
    private static final String ADMIN_CLIENT_ID = "admin-client";
    private static final String NO_ROLE_CLIENT_ID = "no-role-client";

    @ConfigProperty(name = "quarkus.oidc.token-path")
    String authServerUrl;

    final String groupId = getClass().getSimpleName() + "Group";

    @BeforeEach
    protected void beforeEach() throws Exception {
        setupRestAssured();
    }

    @Override
    protected RegistryClient createRestClientV3(Vertx vertx) {
        return RegistryClientFactory.create(RegistryClientOptions.create()
                .registryUrl(registryV3ApiUrl)
                .vertx(vertx)
                .oauth2(authServerUrl, ADMIN_CLIENT_ID, "test1"));
    }

    @Test
    public void testReadOperationWithNoRole() throws Exception {
        // Read-only operation should work with credentials but no role.
        RegistryClient client = RegistryClientFactory.create(RegistryClientOptions.create()
                .registryUrl(registryV3ApiUrl)
                .vertx(vertx)
                .oauth2(authServerUrl, NO_ROLE_CLIENT_ID, "test1"));
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
