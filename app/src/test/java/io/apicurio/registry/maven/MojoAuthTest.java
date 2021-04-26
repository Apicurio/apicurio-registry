/*
 * Copyright 2021 Red Hat
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

package io.apicurio.registry.maven;

import io.apicurio.registry.auth.Auth;
import io.apicurio.registry.auth.AuthTestProfile;
import io.apicurio.registry.auth.KeycloakAuth;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.RegistryClientFactory;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;

@QuarkusTest
@TestProfile(AuthTestProfile.class)
public class MojoAuthTest extends RegistryMojoTestBase {

    @ConfigProperty(name = "registry.keycloak.url")
    String authServerUrl;

    @ConfigProperty(name = "registry.keycloak.realm")
    String realm;

    @ConfigProperty(name = "quarkus.oidc.tenant-enabled")
    Boolean authEnabled;

    String adminClientId = "registry-api";

    String clientSecret = "test1";

    String testUsername = "sr-test-user";
    String testPassword = "sr-test-password";

    private RegistryClient createClient(Auth auth) {
        return RegistryClientFactory.create(registryV2ApiUrl, Collections.emptyMap(), auth);
    }

    /**
     * @see io.apicurio.registry.AbstractResourceTestBase#createRestClientV2()
     */
    @Override
    protected RegistryClient createRestClientV2() {
        System.out.println("Auth is " + authEnabled);
        Auth auth = new KeycloakAuth(authServerUrl, realm, adminClientId, "test1");
        return this.createClient(auth);
    }

    @Test
    public void testRegister() throws IOException, MojoFailureException, MojoExecutionException {
        System.out.println("Auth is " + authEnabled);

        RegisterRegistryMojo registerRegistryMojo = new RegisterRegistryMojo();
        registerRegistryMojo.registryUrl = TestUtils.getRegistryV2ApiUrl();
        registerRegistryMojo.authServerUrl = authServerUrl;
        registerRegistryMojo.realm = realm;
        registerRegistryMojo.clientId = adminClientId;
        registerRegistryMojo.clientSecret = clientSecret;

        super.testRegister(registerRegistryMojo, "testRegister");
    }

    @Test
    public void testBasicAuth() throws IOException, MojoFailureException, MojoExecutionException {
        System.out.println("Auth is " + authEnabled);

        RegisterRegistryMojo registerRegistryMojo = new RegisterRegistryMojo();
        registerRegistryMojo.setClient(null);

        registerRegistryMojo.registryUrl = TestUtils.getRegistryV2ApiUrl();
        registerRegistryMojo.username = testUsername;
        registerRegistryMojo.password = testPassword;

        super.testRegister(registerRegistryMojo, "testBasicAuth");
    }
}
