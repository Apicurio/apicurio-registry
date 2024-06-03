package io.apicurio.tests.auth;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.Rule;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rest.client.models.UserInfo;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.AuthTestProfile;
import io.apicurio.registry.utils.tests.JWKSMockServer;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.apicurio.tests.utils.Constants;
import io.kiota.http.vertx.VertXRequestAdapter;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;
import io.vertx.ext.web.client.WebClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.apicurio.registry.client.auth.VertXAuthFactory.buildOIDCWebClient;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag(Constants.AUTH)
@TestProfile(AuthTestProfile.class)
@QuarkusIntegrationTest
public class SimpleAuthIT extends ApicurioRegistryBaseIT {

    final String groupId = "authTestGroupId";

    private static final CreateArtifact createArtifact = new CreateArtifact();

    static {
        createArtifact.setArtifactType(ArtifactType.JSON);
        createArtifact.setFirstVersion(new CreateVersion());
        createArtifact.getFirstVersion().setContent(new VersionContent());
        createArtifact.getFirstVersion().getContent().setContentType(ContentTypes.APPLICATION_JSON);
        createArtifact.getFirstVersion().getContent().setContent("{}");
    }

    @Override
    public void cleanArtifacts() throws Exception {
        //Don't clean
    }

    @Override
    protected RegistryClient createRegistryClient() {
        var auth = buildOIDCWebClient(authServerUrlConfigured, JWKSMockServer.ADMIN_CLIENT_ID, "test1");
        return createClient(auth);
    }

    private RegistryClient createClient(WebClient auth) {
        var adapter = new VertXRequestAdapter(auth);
        adapter.setBaseUrl(getRegistryV3ApiUrl());
        return new RegistryClient(adapter);
    }

    @Test
    public void testWrongCreds() throws Exception {
        var auth = buildOIDCWebClient(authServerUrlConfigured, JWKSMockServer.WRONG_CREDS_CLIENT_ID, "test55");
        RegistryClient client = createClient(auth);
        var exception = Assertions.assertThrows(Exception.class, () -> {
            client.groups().byGroupId("foo").artifacts().get();
        });
        assertNotAuthorized(exception);
    }

    @Test
    public void testReadOnly() throws Exception {
        var adapter = new VertXRequestAdapter(buildOIDCWebClient(authServerUrlConfigured, JWKSMockServer.READONLY_CLIENT_ID, "test1"));
        adapter.setBaseUrl(getRegistryV3ApiUrl());
        RegistryClient client = new RegistryClient(adapter);
        String artifactId = TestUtils.generateArtifactId();
        client.groups().byGroupId(groupId).artifacts().get();
        var exception1 = Assertions.assertThrows(Exception.class, () -> {
            client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get();
        });
        assertArtifactNotFound(exception1);
        var exception2 = Assertions.assertThrows(Exception.class, () -> {
            client.groups().byGroupId("abc").artifacts().byArtifactId(artifactId).get();
        });
        assertArtifactNotFound(exception2);
        createArtifact.setArtifactId(artifactId);
        var exception3 = Assertions.assertThrows(Exception.class, () -> {
            client.groups().byGroupId("testReadOnly").artifacts().post(createArtifact);
        });
        assertForbidden(exception3);

        var devAdapter = new VertXRequestAdapter(buildOIDCWebClient(authServerUrlConfigured, JWKSMockServer.DEVELOPER_CLIENT_ID, "test1"));
        devAdapter.setBaseUrl(getRegistryV3ApiUrl());
        RegistryClient devClient = new RegistryClient(devAdapter);

        VersionMetaData meta = devClient.groups().byGroupId(groupId).artifacts().post(createArtifact).getVersion();

        TestUtils.retry(() -> devClient.groups().byGroupId(groupId).artifacts().byArtifactId(meta.getArtifactId()).get());

        assertNotNull(client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get());

        UserInfo userInfo = client.users().me().get();
        assertNotNull(userInfo);
        Assertions.assertEquals("readonly-client", userInfo.getUsername());
        Assertions.assertFalse(userInfo.getAdmin());
        Assertions.assertFalse(userInfo.getDeveloper());
        Assertions.assertTrue(userInfo.getViewer());
    }

    @Test
    public void testDevRole() throws Exception {
        var adapter = new VertXRequestAdapter(buildOIDCWebClient(authServerUrlConfigured, JWKSMockServer.DEVELOPER_CLIENT_ID, "test1"));
        adapter.setBaseUrl(getRegistryV3ApiUrl());
        RegistryClient client = new RegistryClient(adapter);
        String artifactId = TestUtils.generateArtifactId();
        try {
            client.groups().byGroupId(groupId).artifacts().get();

            createArtifact.setArtifactId(artifactId);
            client.groups().byGroupId(groupId).artifacts().post(createArtifact);
            TestUtils.retry(() -> client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get());

            Assertions.assertTrue(client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression("branch=latest").content().get().readAllBytes().length > 0);

            Rule ruleConfig = new Rule();
            ruleConfig.setType(RuleType.VALIDITY);
            ruleConfig.setConfig(ValidityLevel.NONE.name());
            client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).rules().post(ruleConfig);

            var exception = Assertions.assertThrows(Exception.class, () -> {
                client.admin().rules().post(ruleConfig);
            });
            assertForbidden(exception);

            UserInfo userInfo = client.users().me().get();
            assertNotNull(userInfo);
            Assertions.assertEquals("developer-client", userInfo.getUsername());
            Assertions.assertFalse(userInfo.getAdmin());
            Assertions.assertTrue(userInfo.getDeveloper());
            Assertions.assertFalse(userInfo.getViewer());
        } finally {
            client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).delete();
        }
    }

    @Test
    public void testAdminRole() throws Exception {
        var adapter = new VertXRequestAdapter(buildOIDCWebClient(authServerUrlConfigured, JWKSMockServer.ADMIN_CLIENT_ID, "test1"));
        adapter.setBaseUrl(getRegistryV3ApiUrl());
        RegistryClient client = new RegistryClient(adapter);
        String artifactId = TestUtils.generateArtifactId();
        try {
            client.groups().byGroupId(groupId).artifacts().get();

            createArtifact.setArtifactId(artifactId);
            client.groups().byGroupId(groupId).artifacts().post(createArtifact);
            TestUtils.retry(() -> client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get());

            Assertions.assertTrue(client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression("branch=latest").content().get().readAllBytes().length > 0);

            Rule ruleConfig = new Rule();
            ruleConfig.setType(RuleType.VALIDITY);
            ruleConfig.setConfig(ValidityLevel.NONE.name());
            client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).rules().post(ruleConfig);

            client.admin().rules().post(ruleConfig);

            UserInfo userInfo = client.users().me().get();
            assertNotNull(userInfo);
            Assertions.assertEquals("admin-client", userInfo.getUsername());
            Assertions.assertTrue(userInfo.getAdmin());
            Assertions.assertFalse(userInfo.getDeveloper());
            Assertions.assertFalse(userInfo.getViewer());
        } finally {
            client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).delete();
        }
    }

    protected void assertArtifactNotFound(Exception exception) {
        Assertions.assertEquals(io.apicurio.registry.rest.client.models.Error.class, exception.getClass());
        Assertions.assertEquals("ArtifactNotFoundException", ((io.apicurio.registry.rest.client.models.Error) exception).getName());
        Assertions.assertEquals(404, ((io.apicurio.registry.rest.client.models.Error) exception).getErrorCode());
    }
}