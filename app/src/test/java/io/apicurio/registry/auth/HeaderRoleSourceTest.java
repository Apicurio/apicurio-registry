package io.apicurio.registry.auth;



import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.AuthTestProfileWithHeaderRoles;
import io.apicurio.registry.utils.tests.JWKSMockServer;
import io.kiota.http.vertx.VertXRequestAdapter;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.apicurio.registry.client.auth.VertXAuthFactory.buildOIDCWebClient;


@QuarkusTest
@TestProfile(AuthTestProfileWithHeaderRoles.class)
@Tag(ApicurioTestTags.SLOW)
public class HeaderRoleSourceTest extends AbstractResourceTestBase {

    private static final String TEST_CONTENT = "{\r\n" +
            "    \"type\" : \"record\",\r\n" +
            "    \"name\" : \"userInfo\",\r\n" +
            "    \"namespace\" : \"my.example\",\r\n" +
            "    \"fields\" : [{\"name\" : \"age\", \"type\" : \"int\"}]\r\n" +
            "} ";

    @ConfigProperty(name = "registry.auth.token.endpoint")
    @Info(category = "auth", description = "Auth token endpoint", availableSince = "2.1.0.Final")
    String authServerUrlConfigured;

    @Override
    protected RegistryClient createRestClientV3() {
        var adapter = new VertXRequestAdapter(buildOIDCWebClient(authServerUrlConfigured, JWKSMockServer.ADMIN_CLIENT_ID, "test1"));
        adapter.setBaseUrl(registryV3ApiUrl);
        return new RegistryClient(adapter);
    }

    @Test
    public void testLocalRoles() throws Exception {
        var content = new io.apicurio.registry.rest.client.models.ArtifactContent();
        content.setContent(TEST_CONTENT);

        var rule = new io.apicurio.registry.rest.client.models.Rule();
        rule.setConfig(ValidityLevel.FULL.name());
        rule.setType(io.apicurio.registry.rest.client.models.RuleType.VALIDITY);

        var noRoleAdapter = new VertXRequestAdapter(buildOIDCWebClient(authServerUrlConfigured, JWKSMockServer.NO_ROLE_CLIENT_ID, "test1"));
        noRoleAdapter.setBaseUrl(registryV3ApiUrl);
        var noRoleClient = new RegistryClient(noRoleAdapter);

        var readAdapter = new VertXRequestAdapter(buildOIDCWebClient(authServerUrlConfigured, JWKSMockServer.READONLY_CLIENT_ID, "test1"));
        readAdapter.setBaseUrl(registryV3ApiUrl);
        var readClient = new RegistryClient(readAdapter);

        var devAdapter = new VertXRequestAdapter(buildOIDCWebClient(authServerUrlConfigured, JWKSMockServer.DEVELOPER_CLIENT_ID, "test1"));
        devAdapter.setBaseUrl(registryV3ApiUrl);
        var devClient = new RegistryClient(devAdapter);

        var adminAdapter = new VertXRequestAdapter(buildOIDCWebClient(authServerUrlConfigured, JWKSMockServer.ADMIN_CLIENT_ID, "test1"));
        adminAdapter.setBaseUrl(registryV3ApiUrl);
        var adminClient = new RegistryClient(adminAdapter);


        // User is authenticated but no roles assigned - operations should fail.
        var exception1 = Assertions.assertThrows(Exception.class, () -> {
            noRoleClient.groups().byGroupId("default").artifacts().get();
        });
        assertForbidden(exception1);

        var exception2 = Assertions.assertThrows(Exception.class, () -> {
            noRoleClient
                .groups()
                .byGroupId(UUID.randomUUID().toString())
                .artifacts()
                .post(content, config -> {
                    config.headers.add("X-Registry-ArtifactId", getClass().getSimpleName());
                });
        });
        assertForbidden(exception2);

        var exception3 = Assertions.assertThrows(Exception.class, () -> {
            noRoleClient.admin().rules().post(rule);
        });
        assertForbidden(exception3);


        // Now using the read client user should be able to read but nothing else
        readClient.groups().byGroupId("default").artifacts().get(config -> {
            config.headers.add("X-Registry-Role", "sr-readonly");
        });
        var exception4 = Assertions.assertThrows(Exception.class, () -> {
            readClient
                    .groups()
                    .byGroupId(UUID.randomUUID().toString())
                    .artifacts()
                    .post(content, config -> {
                        config.headers.add("X-Registry-ArtifactId", getClass().getSimpleName());
                        config.headers.add("X-Registry-Role", "sr-readonly");
                    });
        });
        assertForbidden(exception4);

        var exception5 = Assertions.assertThrows(Exception.class, () -> {
            readClient.admin().rules().post(rule, config -> {
                config.headers.add("X-Registry-Role", "sr-readonly");
            });
        });
        assertForbidden(exception5);

        // the user can read and write with the developer client but not admin
        devClient.groups().byGroupId("default").artifacts().get(config -> {
            config.headers.add("X-Registry-Role", "sr-developer");
        });
        devClient
            .groups()
            .byGroupId(UUID.randomUUID().toString())
            .artifacts()
            .post(content, config -> {
                config.headers.add("X-Registry-ArtifactId", getClass().getSimpleName());
                config.headers.add("X-Registry-Role", "sr-developer");
            });
        var exception6 = Assertions.assertThrows(Exception.class, () -> {
            devClient.admin().rules().post(rule, config -> {
                config.headers.add("X-Registry-Role", "sr-developer");
            });
        });
        assertForbidden(exception6);

        // the user can do everything with the admin client
        adminClient.groups().byGroupId("default").artifacts().get(config -> {
            config.headers.add("X-Registry-Role", "sr-admin");
        });
        adminClient
                .groups()
                .byGroupId(UUID.randomUUID().toString())
                .artifacts()
                .post(content, config -> {
                    config.headers.add("X-Registry-ArtifactId", getClass().getSimpleName());
                    config.headers.add("X-Registry-Role", "sr-admin");
                });
        adminClient.admin().rules().post(rule, config -> {
            config.headers.add("X-Registry-Role", "sr-admin");
        });
    }
}
