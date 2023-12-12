package io.apicurio.registry.auth;

import com.microsoft.kiota.ApiException;
import com.microsoft.kiota.authentication.AnonymousAuthenticationProvider;
import com.microsoft.kiota.authentication.BaseBearerTokenAuthenticationProvider;
import com.microsoft.kiota.http.OkHttpRequestAdapter;
import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.AuthTestProfileAnonymousCredentials;
import io.apicurio.registry.utils.tests.JWKSMockServer;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

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
        var adapter = new OkHttpRequestAdapter(new BaseBearerTokenAuthenticationProvider(
                new OidcAccessTokenProvider(authServerUrl, JWKSMockServer.WRONG_CREDS_CLIENT_ID, "secret")));
        adapter.setBaseUrl(registryV3ApiUrl);
        RegistryClient client = new RegistryClient(adapter);
        var executionException = Assertions.assertThrows(ExecutionException.class, () -> {
            client.groups().byGroupId(groupId).artifacts().get().get(3, TimeUnit.SECONDS);
        });
        assertNotAuthorized(executionException);
    }

    @Test
    public void testNoCredentials() throws Exception {
        var adapter = new OkHttpRequestAdapter(new AnonymousAuthenticationProvider());
        adapter.setBaseUrl(registryV3ApiUrl);
        RegistryClient client = new RegistryClient(adapter);
        // Read-only operation should work without any credentials.
        var results = client.search().artifacts().get(config -> config.queryParameters.group = groupId).get(3,
                TimeUnit.SECONDS);
        Assertions.assertTrue(results.getCount() >= 0);

        // Write operation should fail without any credentials
        String data = "{\r\n" + "    \"type\" : \"record\",\r\n" + "    \"name\" : \"userInfo\",\r\n"
                + "    \"namespace\" : \"my.example\",\r\n"
                + "    \"fields\" : [{\"name\" : \"age\", \"type\" : \"int\"}]\r\n" + "}";
        var executionException = Assertions.assertThrows(ExecutionException.class, () -> {
            var content = new io.apicurio.registry.rest.client.models.ArtifactContent();
            content.setContent(data);
            client.groups().byGroupId(groupId).artifacts().post(content, config -> {
                config.headers.add("X-Registry-ArtifactType", ArtifactType.AVRO);
                config.headers.add("X-Registry-ArtifactId", "testNoCredentials");
            }).get(3, TimeUnit.SECONDS);
        });
        Assertions.assertNotNull(executionException.getCause());
        Assertions.assertEquals(ApiException.class, executionException.getCause().getClass());
        Assertions.assertEquals(401, ((ApiException) executionException.getCause()).getResponseStatusCode());
    }
}
