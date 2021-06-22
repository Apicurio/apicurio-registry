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

package io.apicurio.multitenant;

import io.apicurio.multitenant.client.Auth;
import io.apicurio.multitenant.client.TenantManagerClient;
import io.apicurio.multitenant.client.TenantManagerClientImpl;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.AuthTestProfileWithoutRoles;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.keycloak.authorization.client.util.HttpResponseException;

import javax.enterprise.inject.Typed;


@QuarkusTest
@TestProfile(AuthTestProfileWithoutRoles.class)
@Tag(ApicurioTestTags.DOCKER)
@Typed(TenantManagerClientAuthTest.class)
public class TenantManagerClientAuthTest extends TenantManagerClientTest {

    @ConfigProperty(name = "tenant-manager.keycloak.url")
    String authServerUrl;

    @ConfigProperty(name = "tenant-manager.keycloak.realm")
    String realm;

    String clientId = "registry-api";

    private TenantManagerClient createClient(Auth auth) {
        return new TenantManagerClientImpl("http://localhost:8081/", auth);
    }

    @Override
    protected TenantManagerClient createRestClient() {
        Auth auth = new Auth(authServerUrl, realm, clientId, "test1");
        return this.createClient(auth);
    }

    @Test
    public void testWrongCreds() throws Exception {
        Auth auth = new Auth(authServerUrl, realm, clientId, "wrongsecret");
        TenantManagerClient client = createClient(auth);
        Assertions.assertThrows(HttpResponseException.class, client::listTenants);
    }
}
