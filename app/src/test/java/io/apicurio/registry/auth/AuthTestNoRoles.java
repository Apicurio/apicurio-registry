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

package io.apicurio.registry.auth;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.RegistryClientFactory;
import io.apicurio.registry.rest.v2.beans.Rule;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.AuthTestProfileWithoutRoles;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.rest.client.auth.Auth;
import io.apicurio.rest.client.auth.OidcAuth;
import io.apicurio.rest.client.auth.exception.AuthErrorHandler;
import io.apicurio.rest.client.auth.exception.NotAuthorizedException;
import io.apicurio.rest.client.spi.ApicurioHttpClient;
import io.apicurio.rest.client.spi.ApicurioHttpClientFactory;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@TestProfile(AuthTestProfileWithoutRoles.class)
@Tag(ApicurioTestTags.DOCKER)
public class AuthTestNoRoles extends AbstractResourceTestBase {

    @ConfigProperty(name = "registry.auth.token.endpoint")
    String authServerUrlConfigured;

    String noRoleClientId = "registry-api-no-role";

    final String groupId = "authTestGroupId";

    ApicurioHttpClient httpClient;

    private RegistryClient createClient(Auth auth) {
        return RegistryClientFactory.create(registryV2ApiUrl, Collections.emptyMap(), auth);
    }

    /**
     * @see io.apicurio.registry.AbstractResourceTestBase#createRestClientV2()
     */
    @Override
    protected RegistryClient createRestClientV2() {
        httpClient = ApicurioHttpClientFactory.create(authServerUrlConfigured, new AuthErrorHandler());
        Auth auth = new OidcAuth(httpClient, noRoleClientId, "test1");
        return this.createClient(auth);
    }

    @Test
    public void testWrongCreds() throws Exception {
        Auth auth = new OidcAuth(httpClient, noRoleClientId, "test55");
        RegistryClient client = createClient(auth);
        Assertions.assertThrows(NotAuthorizedException.class, () -> {
            client.listArtifactsInGroup(groupId);
        });
    }


    @Test
    public void testAdminRole() throws Exception {
        Auth auth = new OidcAuth(httpClient, noRoleClientId, "test1");
        RegistryClient client = createClient(auth);
        String artifactId = TestUtils.generateArtifactId();
        try {
            client.listArtifactsInGroup(groupId);
            client.createArtifact(groupId, artifactId, ArtifactType.JSON, new ByteArrayInputStream("{}".getBytes()));
            TestUtils.retry(() -> client.getArtifactMetaData(groupId, artifactId));
            assertNotNull(client.getLatestArtifact(groupId, artifactId));
            Rule ruleConfig = new Rule();
            ruleConfig.setType(RuleType.VALIDITY);
            ruleConfig.setConfig(ValidityLevel.NONE.name());
            client.createArtifactRule(groupId, artifactId, ruleConfig);

            client.createGlobalRule(ruleConfig);
        } finally {
            client.deleteArtifact(groupId, artifactId);
        }
    }
}
