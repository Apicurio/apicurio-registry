package io.apicurio.registry.auth;



import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.maven.RegisterRegistryMojo;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.noprofile.maven.RegistryMojoTestBase;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.AuthTestProfile;
import io.apicurio.registry.utils.tests.JWKSMockServer;
import io.apicurio.registry.utils.tests.TestUtils;
import io.kiota.http.vertx.VertXRequestAdapter;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.apicurio.registry.client.auth.VertXAuthFactory.buildOIDCWebClient;

@QuarkusTest
@TestProfile(AuthTestProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class MojoAuthTest extends RegistryMojoTestBase {

    @ConfigProperty(name = "registry.auth.token.endpoint")
    @Info(category = "auth", description = "Auth token endpoint", availableSince = "2.1.0.Final")
    String authServerUrlConfigured;

    @ConfigProperty(name = "quarkus.oidc.tenant-enabled")
    @Info(category = "auth", description = "OIDC tenant enabled", availableSince = "2.0.0.Final")
    Boolean authEnabled;

    String clientSecret = "test1";

    String clientScope = "testScope";

    String testUsername = "sr-test-user";
    String testPassword = "sr-test-password";

    @Override
    protected RegistryClient createRestClientV3() {
        var adapter = new VertXRequestAdapter(buildOIDCWebClient(authServerUrlConfigured, JWKSMockServer.ADMIN_CLIENT_ID, "test1"));
        adapter.setBaseUrl(registryV3ApiUrl);
        return new RegistryClient(adapter);
    }

    @Test
    public void testRegister() throws IOException, MojoFailureException, MojoExecutionException {
        System.out.println("Auth is " + authEnabled);

        RegisterRegistryMojo registerRegistryMojo = new RegisterRegistryMojo();
        registerRegistryMojo.setRegistryUrl(TestUtils.getRegistryV3ApiUrl(testPort));
        registerRegistryMojo.setAuthServerUrl(authServerUrlConfigured);
        registerRegistryMojo.setClientId(JWKSMockServer.ADMIN_CLIENT_ID);
        registerRegistryMojo.setClientSecret(clientSecret);
        registerRegistryMojo.setClientScope(clientScope);

        super.testRegister(registerRegistryMojo, "testRegister");
    }

    @Test
    public void testBasicAuth() throws IOException, MojoFailureException, MojoExecutionException {
        System.out.println("Auth is " + authEnabled);

        RegisterRegistryMojo registerRegistryMojo = new RegisterRegistryMojo();
        registerRegistryMojo.setClient(null);

        registerRegistryMojo.setRegistryUrl(TestUtils.getRegistryV3ApiUrl(testPort));
        registerRegistryMojo.setUsername(testUsername);
        registerRegistryMojo.setPassword(testPassword);

        super.testRegister(registerRegistryMojo, "testBasicAuth");
    }
}
