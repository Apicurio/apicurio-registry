package io.apicurio.registry.auth;

import com.microsoft.kiota.authentication.BaseBearerTokenAuthenticationProvider;
import com.microsoft.kiota.http.OkHttpRequestAdapter;
import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.ArtifactContent;
import io.apicurio.registry.rest.client.models.Rule;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.AuthTestProfile;
import io.apicurio.registry.utils.tests.JWKSMockServer;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@TestProfile(AuthTestProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class AuthTestNoRoles extends AbstractResourceTestBase {

    @ConfigProperty(name = "registry.auth.token.endpoint")
    @Info(category = "auth", description = "Auth token endpoint", availableSince = "2.1.0.Final")
    String authServerUrlConfigured;

    final String groupId = "authTestGroupId";

    @Override
    protected RegistryClient createRestClientV3() {
        var adapter = new OkHttpRequestAdapter(
                new BaseBearerTokenAuthenticationProvider(
                        new OidcAccessTokenProvider(authServerUrlConfigured, JWKSMockServer.ADMIN_CLIENT_ID, "test1")));
        adapter.setBaseUrl(registryV3ApiUrl);
        return new RegistryClient(adapter);
    }

    @Test
    public void testWrongCreds() throws Exception {
        var adapter = new OkHttpRequestAdapter(
                new BaseBearerTokenAuthenticationProvider(
                        new OidcAccessTokenProvider(authServerUrlConfigured, JWKSMockServer.WRONG_CREDS_CLIENT_ID, "test55")));
        adapter.setBaseUrl(registryV3ApiUrl);
        RegistryClient client = new RegistryClient(adapter);
        var executionException = Assertions.assertThrows(ExecutionException.class, () -> {
            client.groups().byGroupId(groupId).artifacts().get().get(3, TimeUnit.SECONDS);
        });
        assertNotAuthorized(executionException);
    }

    @Test
    public void testAdminRole() throws Exception {
        var adapter = new OkHttpRequestAdapter(
                new BaseBearerTokenAuthenticationProvider(
                        new OidcAccessTokenProvider(authServerUrlConfigured, JWKSMockServer.ADMIN_CLIENT_ID, "test1")));
        adapter.setBaseUrl(registryV3ApiUrl);
        RegistryClient client = new RegistryClient(adapter);
        String artifactId = TestUtils.generateArtifactId();
        try {
            client.groups().byGroupId("default").artifacts().get().get(3, TimeUnit.SECONDS);
            ArtifactContent content = new ArtifactContent();
            content.setContent("{}");
            client
                    .groups()
                    .byGroupId(groupId)
                    .artifacts()
                    .post(content, config -> {
                        config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
                        config.headers.add("X-Registry-ArtifactId", artifactId);
                    }).get(3, TimeUnit.SECONDS);
            TestUtils.retry(() ->
                    client
                        .groups()
                        .byGroupId(groupId)
                        .artifacts()
                        .byArtifactId(artifactId)
                        .meta()
                        .get()
                        .get(3, TimeUnit.SECONDS));
            assertNotNull(client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get().get(3, TimeUnit.SECONDS));

            Rule ruleConfig = new Rule();
            ruleConfig.setType(RuleType.VALIDITY);
            ruleConfig.setConfig(ValidityLevel.NONE.name());

            client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).rules().post(ruleConfig);
            client.admin().rules().post(ruleConfig).get(3, TimeUnit.SECONDS);
        } finally {
            client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).delete().get(3, TimeUnit.SECONDS);
        }
    }
}
