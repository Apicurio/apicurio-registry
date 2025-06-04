package io.apicurio.registry.auth;

import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.client.auth.VertXAuthFactory;
import io.apicurio.registry.maven.RegisterRegistryMojo;
import io.apicurio.registry.noprofile.maven.RegistryMojoTestBase;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.AuthTestProfile;
import io.apicurio.registry.utils.tests.KeycloakTestContainerManager;
import io.apicurio.registry.utils.tests.TestUtils;
import io.kiota.http.vertx.VertXRequestAdapter;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.vertx.core.Vertx;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.apicurio.common.apps.config.Info.CATEGORY_AUTH;

@QuarkusTest
@TestProfile(AuthTestProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class MojoAuthTest extends RegistryMojoTestBase {

    @ConfigProperty(name = "quarkus.oidc.token-path")
    @Info(category = CATEGORY_AUTH, description = "Auth token endpoint", availableSince = "2.1.0.Final")
    String authServerUrlConfigured;

    @ConfigProperty(name = "quarkus.oidc.tenant-enabled")
    @Info(category = CATEGORY_AUTH, description = "OIDC tenant enabled", availableSince = "2.0.0.Final")
    Boolean authEnabled;

    String clientSecret = "test1";

    String clientScope = "openid";

    String testUsername = "developer-client";
    String testPassword = clientSecret;

    @Override
    protected RegistryClient createRestClientV3(Vertx vertx) {
        var adapter = new VertXRequestAdapter(VertXAuthFactory.buildOIDCWebClient(vertx,
                authServerUrlConfigured, KeycloakTestContainerManager.ADMIN_CLIENT_ID, "test1"));
        adapter.setBaseUrl(registryV3ApiUrl);
        return new RegistryClient(adapter);
    }

    @Test
    public void testRegister() throws IOException, MojoFailureException, MojoExecutionException {
        System.out.println("Auth is " + authEnabled);

        RegisterRegistryMojo registerRegistryMojo = new RegisterRegistryMojo();
        registerRegistryMojo.setRegistryUrl(TestUtils.getRegistryV3ApiUrl(testPort));
        registerRegistryMojo.setAuthServerUrl(authServerUrlConfigured);
        registerRegistryMojo.setClientId(KeycloakTestContainerManager.ADMIN_CLIENT_ID);
        registerRegistryMojo.setClientSecret(clientSecret);
        registerRegistryMojo.setClientScope(clientScope);

        super.testRegister(registerRegistryMojo, "testRegister");
    }

    @Test
    public void testBasicAuth() throws IOException, MojoFailureException, MojoExecutionException {
        System.out.println("Auth is " + authEnabled);

        RegisterRegistryMojo registerRegistryMojo = new RegisterRegistryMojo();
        registerRegistryMojo.setRegistryUrl(TestUtils.getRegistryV3ApiUrl(testPort));
        registerRegistryMojo.setUsername(testUsername);
        registerRegistryMojo.setPassword(testPassword);

        super.testRegister(registerRegistryMojo, "testBasicAuth");
    }
}
