package io.apicurio.registry.auth;

import com.microsoft.kiota.authentication.BaseBearerTokenAuthenticationProvider;
import com.microsoft.kiota.http.OkHttpRequestAdapter;
import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.ArtifactContent;
import io.apicurio.registry.rest.client.models.RoleMapping;
import io.apicurio.registry.rest.client.models.RoleType;
import io.apicurio.registry.rest.client.models.Rule;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rest.client.models.UpdateRole;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.AuthTestProfileWithLocalRoles;
import io.apicurio.registry.utils.tests.JWKSMockServer;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Tests local role mappings (managed in the database via the role-mapping API).
 */
@QuarkusTest
@TestProfile(AuthTestProfileWithLocalRoles.class)
@Tag(ApicurioTestTags.SLOW)
public class AuthTestLocalRoles extends AbstractResourceTestBase {

    private static final String TEST_CONTENT = "{\r\n" + "    \"type\" : \"record\",\r\n"
            + "    \"name\" : \"userInfo\",\r\n" + "    \"namespace\" : \"my.example\",\r\n"
            + "    \"fields\" : [{\"name\" : \"age\", \"type\" : \"int\"}]\r\n" + "} ";

    @ConfigProperty(name = "registry.auth.token.endpoint")
    @Info(category = "auth", description = "Auth token endpoint", availableSince = "2.1.0.Final")
    String authServerUrlConfigured;

    @Override
    protected RegistryClient createRestClientV3() {
        var adapter = new OkHttpRequestAdapter(
                new BaseBearerTokenAuthenticationProvider(new OidcAccessTokenProvider(authServerUrlConfigured,
                        JWKSMockServer.ADMIN_CLIENT_ID, "test1")));
        adapter.setBaseUrl(registryV3ApiUrl);
        return new RegistryClient(adapter);
    }

    private static final ArtifactContent content = new ArtifactContent();
    private static final Rule rule = new Rule();

    static {
        content.setContent(TEST_CONTENT);

        rule.setConfig(ValidityLevel.FULL.name());
        rule.setType(RuleType.VALIDITY);
    }

    @Test
    public void testLocalRoles() throws Exception {
        var adapterAdmin = new OkHttpRequestAdapter(
                new BaseBearerTokenAuthenticationProvider(new OidcAccessTokenProvider(authServerUrlConfigured,
                        JWKSMockServer.ADMIN_CLIENT_ID, "test1")));
        adapterAdmin.setBaseUrl(registryV3ApiUrl);
        RegistryClient clientAdmin = new RegistryClient(adapterAdmin);

        var adapterAuth = new OkHttpRequestAdapter(
                new BaseBearerTokenAuthenticationProvider(new OidcAccessTokenProvider(authServerUrlConfigured,
                        JWKSMockServer.NO_ROLE_CLIENT_ID, "test1")));
        adapterAuth.setBaseUrl(registryV3ApiUrl);
        RegistryClient client = new RegistryClient(adapterAuth);

        // User is authenticated but no roles assigned yet - operations should fail.
        var executionException1 = Assertions.assertThrows(ExecutionException.class, () -> {
            client.groups().byGroupId("default").artifacts().get().get(3, TimeUnit.SECONDS);
        });
        assertForbidden(executionException1);

        var executionException2 = Assertions.assertThrows(ExecutionException.class, () -> {
            client.groups().byGroupId(UUID.randomUUID().toString()).artifacts()
                    .post(content,
                            config -> config.headers.add("X-Registry-ArtifactId", getClass().getSimpleName()))
                    .get(3, TimeUnit.SECONDS);
        });
        assertForbidden(executionException2);

        var executionException3 = Assertions.assertThrows(ExecutionException.class, () -> {
            client.admin().rules().post(rule).get(3, TimeUnit.SECONDS);
        });
        assertForbidden(executionException3);

        // Now let's grant read-only access to the user.
        var roMapping = new RoleMapping();
        roMapping.setPrincipalId(JWKSMockServer.NO_ROLE_CLIENT_ID);
        roMapping.setRole(RoleType.READ_ONLY);

        clientAdmin.admin().roleMappings().post(roMapping).get(3, TimeUnit.SECONDS);

        // Now the user should be able to read but nothing else
        client.groups().byGroupId("default").artifacts().get().get(3, TimeUnit.SECONDS);

        var executionException4 = Assertions.assertThrows(ExecutionException.class, () -> {
            client.groups().byGroupId(UUID.randomUUID().toString()).artifacts().post(content, config -> {
                config.headers.add("X-Registry-ArtifactType", ArtifactType.AVRO);
                config.headers.add("X-Registry-ArtifactId", getClass().getSimpleName());
            }).get(3, TimeUnit.SECONDS);
        });
        assertForbidden(executionException4);
        var executionException5 = Assertions.assertThrows(ExecutionException.class, () -> {
            client.admin().rules().post(rule).get(3, TimeUnit.SECONDS);
        });
        assertForbidden(executionException5);

        // Now let's update the user's access to Developer
        var devMapping = new UpdateRole();
        devMapping.setRole(RoleType.DEVELOPER);

        clientAdmin.admin().roleMappings().byPrincipalId(JWKSMockServer.NO_ROLE_CLIENT_ID).put(devMapping)
                .get(3, TimeUnit.SECONDS);

        // Now the user can read and write but not admin
        client.groups().byGroupId("default").artifacts().get().get(3, TimeUnit.SECONDS);
        client.groups().byGroupId(UUID.randomUUID().toString()).artifacts().post(content, config -> {
            config.headers.add("X-Registry-ArtifactId", getClass().getSimpleName());
        }).get(3, TimeUnit.SECONDS);
        var executionException6 = Assertions.assertThrows(ExecutionException.class, () -> {
            client.admin().rules().post(rule).get(3, TimeUnit.SECONDS);
        });
        assertForbidden(executionException6);

        // Finally let's update the level to Admin
        var adminMapping = new UpdateRole();
        adminMapping.setRole(RoleType.ADMIN);

        clientAdmin.admin().roleMappings().byPrincipalId(JWKSMockServer.NO_ROLE_CLIENT_ID).put(adminMapping)
                .get(3, TimeUnit.SECONDS);

        // Now the user can do everything
        client.groups().byGroupId("default").artifacts().get().get(3, TimeUnit.SECONDS);
        client.groups().byGroupId(UUID.randomUUID().toString()).artifacts().post(content, config -> {
            config.headers.add("X-Registry-ArtifactId", getClass().getSimpleName());
        }).get(3, TimeUnit.SECONDS);
        client.admin().rules().post(rule).get(3, TimeUnit.SECONDS);
    }
}
