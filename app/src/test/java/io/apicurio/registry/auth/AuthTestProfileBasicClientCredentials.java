package io.apicurio.registry.auth;

import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.model.GroupId;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateRule;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.AuthTestProfile;
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

import static io.apicurio.registry.client.auth.VertXAuthFactory.buildOIDCWebClient;
import static io.apicurio.registry.client.auth.VertXAuthFactory.buildSimpleAuthWebClient;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@TestProfile(AuthTestProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class AuthTestProfileBasicClientCredentials extends AbstractResourceTestBase {

    @ConfigProperty(name = "quarkus.oidc.token-path")
    @Info(category = "auth", description = "Auth token endpoint", availableSince = "2.1.0.Final")
    String authServerUrl;

    final String groupId = "authTestGroupId";

    @Override
    protected RegistryClient createRestClientV3(Vertx vertx) {
        var adapter = new VertXRequestAdapter(buildOIDCWebClient(vertx, authServerUrl,
                KeycloakTestContainerManager.ADMIN_CLIENT_ID, "test1"));
        adapter.setBaseUrl(registryV3ApiUrl);
        return new RegistryClient(adapter);
    }

    @Test
    public void testWrongCreds() throws Exception {
        var adapter = new VertXRequestAdapter(buildSimpleAuthWebClient(vertx,
                KeycloakTestContainerManager.WRONG_CREDS_CLIENT_ID, "test55"));
        adapter.setBaseUrl(registryV3ApiUrl);
        RegistryClient client = new RegistryClient(adapter);
        var exception = Assertions.assertThrows(Exception.class, () -> {
            client.groups().byGroupId(groupId).artifacts().get();
        });
        assertNotAuthorized(exception);
    }

    @Test
    public void testBasicAuthClientCredentials() throws Exception {
        var adapter = new VertXRequestAdapter(
                buildSimpleAuthWebClient(vertx, KeycloakTestContainerManager.ADMIN_CLIENT_ID, "test1"));
        adapter.setBaseUrl(registryV3ApiUrl);
        RegistryClient client = new RegistryClient(adapter);
        String artifactId = TestUtils.generateArtifactId();
        try {
            client.groups().byGroupId(GroupId.DEFAULT.getRawGroupIdWithDefaultString()).artifacts().get();

            CreateArtifact createArtifact = new CreateArtifact();
            createArtifact.setArtifactType(ArtifactType.JSON);
            createArtifact.setArtifactId(artifactId);
            CreateVersion createVersion = new CreateVersion();
            createArtifact.setFirstVersion(createVersion);
            VersionContent versionContent = new VersionContent();
            createVersion.setContent(versionContent);
            versionContent.setContent("{}");
            versionContent.setContentType(ContentTypes.APPLICATION_JSON);

            client.groups().byGroupId(groupId).artifacts().post(createArtifact);

            TestUtils.retry(
                    () -> client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get());
            assertNotNull(client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get());

            CreateRule createRule = new CreateRule();
            createRule.setRuleType(RuleType.VALIDITY);
            createRule.setConfig(ValidityLevel.NONE.name());

            client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).rules().post(createRule);
            client.admin().rules().post(createRule);
        } finally {
            client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).delete();
        }
    }

    @Test
    public void testNoCredentials() throws Exception {
        var adapter = new VertXRequestAdapter(vertx);
        adapter.setBaseUrl(registryV3ApiUrl);
        RegistryClient client = new RegistryClient(adapter);
        var exception = Assertions.assertThrows(Exception.class, () -> {
            client.groups().byGroupId(groupId).artifacts().get();
        });
        assertNotAuthorized(exception);
    }
}
