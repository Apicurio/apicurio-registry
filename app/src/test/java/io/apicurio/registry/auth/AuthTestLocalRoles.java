package io.apicurio.registry.auth;

import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.model.GroupId;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateRule;
import io.apicurio.registry.rest.client.models.RoleMapping;
import io.apicurio.registry.rest.client.models.RoleType;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rest.client.models.UpdateRole;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.AuthTestProfileWithLocalRoles;
import io.apicurio.registry.utils.tests.JWKSMockServer;
import io.apicurio.registry.utils.tests.TestUtils;
import io.kiota.http.vertx.VertXRequestAdapter;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.apicurio.registry.client.auth.VertXAuthFactory.buildOIDCWebClient;

/**
 * Tests local role mappings (managed in the database via the role-mapping API).
 *
 */
@QuarkusTest
@TestProfile(AuthTestProfileWithLocalRoles.class)
@Tag(ApicurioTestTags.SLOW)
public class AuthTestLocalRoles extends AbstractResourceTestBase {

    private static final String TEST_CONTENT = "{\r\n" +
            "    \"type\" : \"record\",\r\n" +
            "    \"name\" : \"userInfo\",\r\n" +
            "    \"namespace\" : \"my.example\",\r\n" +
            "    \"fields\" : [{\"name\" : \"age\", \"type\" : \"int\"}]\r\n" +
            "} ";

    @ConfigProperty(name = "quarkus.oidc.token-path")
    @Info(category = "auth", description = "Auth token endpoint", availableSince = "2.1.0.Final")
    String authServerUrlConfigured;

    @Override
    protected RegistryClient createRestClientV3() {
        var adapter = new VertXRequestAdapter(buildOIDCWebClient(authServerUrlConfigured, JWKSMockServer.ADMIN_CLIENT_ID, "test1"));
        adapter.setBaseUrl(registryV3ApiUrl);
        return new RegistryClient(adapter);
    }

    private static final CreateRule createRule = new CreateRule();
    private static final CreateArtifact createArtifact;

    static {
        createRule.setConfig(ValidityLevel.FULL.name());
        createRule.setRuleType(RuleType.VALIDITY);
        createArtifact = TestUtils.clientCreateArtifact(AuthTestLocalRoles.class.getSimpleName(), ArtifactType.AVRO, TEST_CONTENT, ContentTypes.APPLICATION_JSON);
    }

    @Test
    public void testLocalRoles() throws Exception {
        var adapterAdmin = new VertXRequestAdapter(buildOIDCWebClient(authServerUrlConfigured, JWKSMockServer.ADMIN_CLIENT_ID, "test1"));
        adapterAdmin.setBaseUrl(registryV3ApiUrl);
        RegistryClient clientAdmin = new RegistryClient(adapterAdmin);

        var adapterAuth = new VertXRequestAdapter(buildOIDCWebClient(authServerUrlConfigured, JWKSMockServer.NO_ROLE_CLIENT_ID, "test1"));
        adapterAuth.setBaseUrl(registryV3ApiUrl);
        RegistryClient client = new RegistryClient(adapterAuth);

        // User is authenticated but no roles assigned yet - operations should fail.
        var exception1 = Assertions.assertThrows(Exception.class, () -> {
            client.groups().byGroupId(GroupId.DEFAULT.getRawGroupIdWithDefaultString()).artifacts().get();
        });
        assertForbidden(exception1);

        var exception2 = Assertions.assertThrows(Exception.class, () -> {
            client
                .groups()
                .byGroupId(UUID.randomUUID().toString())
                .artifacts()
                .post(createArtifact)
                ;
        });
        assertForbidden(exception2);

        var exception3 = Assertions.assertThrows(Exception.class, () -> {
            client.admin().rules().post(createRule);
        });
        assertForbidden(exception3);

        // Now let's grant read-only access to the user.
        var roMapping = new RoleMapping();
        roMapping.setPrincipalId(JWKSMockServer.NO_ROLE_CLIENT_ID);
        roMapping.setRole(RoleType.READ_ONLY);

        clientAdmin.admin().roleMappings().post(roMapping);

        // Now the user should be able to read but nothing else
        client.groups().byGroupId(GroupId.DEFAULT.getRawGroupIdWithDefaultString()).artifacts().get();

        var exception4 = Assertions.assertThrows(Exception.class, () -> {
            client
                    .groups()
                    .byGroupId(UUID.randomUUID().toString())
                    .artifacts()
                    .post(createArtifact);
        });
        assertForbidden(exception4);
        var exception5 = Assertions.assertThrows(Exception.class, () -> {
            client.admin().rules().post(createRule);
        });
        assertForbidden(exception5);

        // Now let's update the user's access to Developer
        var devMapping = new UpdateRole();
        devMapping.setRole(RoleType.DEVELOPER);

        clientAdmin
                .admin()
                .roleMappings()
                .byPrincipalId(JWKSMockServer.NO_ROLE_CLIENT_ID)
                .put(devMapping)
                ;

        // Now the user can read and write but not admin
        client.groups().byGroupId(GroupId.DEFAULT.getRawGroupIdWithDefaultString()).artifacts().get();
        client
                .groups()
                .byGroupId(UUID.randomUUID().toString())
                .artifacts()
                .post(createArtifact, config -> {
                    config.headers.add("X-Registry-ArtifactId", getClass().getSimpleName());
                });
        var exception6 = Assertions.assertThrows(Exception.class, () -> {
            client.admin().rules().post(createRule);
        });
        assertForbidden(exception6);

        // Finally let's update the level to Admin
        var adminMapping = new UpdateRole();
        adminMapping.setRole(RoleType.ADMIN);

        clientAdmin
                .admin()
                .roleMappings()
                .byPrincipalId(JWKSMockServer.NO_ROLE_CLIENT_ID)
                .put(adminMapping)
                ;

        // Now the user can do everything
        client.groups().byGroupId(GroupId.DEFAULT.getRawGroupIdWithDefaultString()).artifacts().get();
        client
                .groups()
                .byGroupId(UUID.randomUUID().toString())
                .artifacts()
                .post(createArtifact);
        client.admin().rules().post(createRule);
        
        // Now delete the role mapping
        clientAdmin
                .admin()
                .roleMappings()
                .byPrincipalId(JWKSMockServer.NO_ROLE_CLIENT_ID)
                .delete()
                ;
        
    }
}
