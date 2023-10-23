/*
 * Copyright 2022 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.auth;

import com.microsoft.kiota.authentication.BaseBearerTokenAuthenticationProvider;
import com.microsoft.kiota.http.OkHttpRequestAdapter;
import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.maven.RegisterRegistryMojo;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.noprofile.maven.RegistryMojoTestBase;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.AuthTestProfile;
import io.apicurio.registry.utils.tests.JWKSMockServer;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;

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
    protected RegistryClient createRestClientV2() {
        var adapter = new OkHttpRequestAdapter(
                new BaseBearerTokenAuthenticationProvider(
                        new OidcAccessTokenProvider(authServerUrlConfigured, JWKSMockServer.ADMIN_CLIENT_ID, "test1")));
        adapter.setBaseUrl(registryV2ApiUrl);
        return new RegistryClient(adapter);
    }

    @Test
    public void testRegister() throws IOException, MojoFailureException, MojoExecutionException {
        System.out.println("Auth is " + authEnabled);

        RegisterRegistryMojo registerRegistryMojo = new RegisterRegistryMojo();
        registerRegistryMojo.setRegistryUrl(TestUtils.getRegistryV2ApiUrl(testPort));
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

        registerRegistryMojo.setRegistryUrl(TestUtils.getRegistryV2ApiUrl(testPort));
        registerRegistryMojo.setUsername(testUsername);
        registerRegistryMojo.setPassword(testPassword);

        super.testRegister(registerRegistryMojo, "testBasicAuth");
    }
}
