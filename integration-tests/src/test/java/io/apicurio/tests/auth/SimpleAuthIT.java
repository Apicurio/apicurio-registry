/*
 * Copyright 2023 Red Hat
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

package io.apicurio.tests.auth;

import com.microsoft.kiota.authentication.AuthenticationProvider;
import com.microsoft.kiota.authentication.BaseBearerTokenAuthenticationProvider;
import com.microsoft.kiota.http.OkHttpRequestAdapter;
import io.apicurio.registry.auth.OidcAccessTokenProvider;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.apicurio.tests.utils.Constants;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.*;
import io.apicurio.registry.utils.tests.AuthTestProfile;
import io.apicurio.registry.utils.tests.JWKSMockServer;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;

/**
 * @author Carles Arnal
 */
@Tag(Constants.AUTH)
@TestProfile(AuthTestProfile.class)
@QuarkusIntegrationTest
public class SimpleAuthIT extends ApicurioRegistryBaseIT {

    @Override
    public void cleanArtifacts() throws Exception {
        //Don't clean
    }

    @Override
    protected RegistryClient createRegistryClient() {
        var auth = new BaseBearerTokenAuthenticationProvider(new OidcAccessTokenProvider(authServerUrlConfigured, JWKSMockServer.ADMIN_CLIENT_ID, "test1"));
        return createClient(auth);
    }

    private RegistryClient createClient(AuthenticationProvider auth) {
        var adapter = new OkHttpRequestAdapter(auth);
        adapter.setBaseUrl(getRegistryBaseUrl());
        return new RegistryClient(adapter);
    }

    @Test
    public void testWrongCreds() throws Exception {
        var auth = new BaseBearerTokenAuthenticationProvider(new OidcAccessTokenProvider(authServerUrlConfigured, JWKSMockServer.WRONG_CREDS_CLIENT_ID, "test55"));
        RegistryClient client = createClient(auth);
        var executionException = Assertions.assertThrows(ExecutionException.class, () -> {
            client.groups().byGroupId("foo").artifacts().get().get(3, TimeUnit.SECONDS);
        });
        assertNotAuthorized(executionException);
    }

    @Test
    public void testReadOnly() throws Exception {
        var auth = new BaseBearerTokenAuthenticationProvider(new OidcAccessTokenProvider(authServerUrlConfigured, JWKSMockServer.READONLY_CLIENT_ID, "test1"));
        RegistryClient client = createClient(auth);

        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();
        client.groups().byGroupId(groupId).artifacts().get().get(3, TimeUnit.SECONDS);
        var executionException1 = Assertions.assertThrows(ExecutionException.class, () -> client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).meta().get().get(3, TimeUnit.SECONDS));
        assertNotAuthorized(executionException1);
        var executionException2 = Assertions.assertThrows(ExecutionException.class, () -> client.groups().byGroupId("abc").artifacts().byArtifactId(artifactId).get().get(3, TimeUnit.SECONDS));
        assertNotAuthorized(executionException2);
        var executionException3 = Assertions.assertThrows(ExecutionException.class, () -> {
            ArtifactContent content = new ArtifactContent();
            content.setContent("{}");
            client.groups().byGroupId("ccc").artifacts().post(content, config -> {
                config.headers.add("X-Registry-ArtifactId", artifactId);
                config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
            }).get(3, TimeUnit.SECONDS);
        });
        assertForbidden(executionException3);
        {
            var devAuth = new BaseBearerTokenAuthenticationProvider(new OidcAccessTokenProvider(authServerUrlConfigured, JWKSMockServer.DEVELOPER_CLIENT_ID, "test1"));
            RegistryClient devClient = createClient(devAuth);
            ArtifactContent content = new ArtifactContent();
            content.setContent("{}");
            ArtifactMetaData meta = devClient.groups().byGroupId("ccc").artifacts().post(content, config -> {
                config.headers.add("X-Registry-ArtifactId", artifactId);
                config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
            }).get(3, TimeUnit.SECONDS);
            TestUtils.retry(() -> devClient.groups().byGroupId(groupId).artifacts().byArtifactId(meta.getId()).meta().get().get(3, TimeUnit.SECONDS));
        }
        assertTrue(client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get().get(3, TimeUnit.SECONDS).readAllBytes().length > 0);
    }

    @Test
    public void testDevRole() throws Exception {
        var devAuth = new BaseBearerTokenAuthenticationProvider(new OidcAccessTokenProvider(authServerUrlConfigured, JWKSMockServer.READONLY_CLIENT_ID, "test1"));
        RegistryClient client = createClient(devAuth);

        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();
        try {
            client.groups().byGroupId(groupId).artifacts().get().get(3, TimeUnit.SECONDS);

            ArtifactContent content = new ArtifactContent();
            content.setContent("{}");
            client.groups().byGroupId("ccc").artifacts().post(content, config -> {
                config.headers.add("X-Registry-ArtifactId", artifactId);
                config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
            }).get(3, TimeUnit.SECONDS);
            TestUtils.retry(() -> client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).meta().get().get(3, TimeUnit.SECONDS));

            assertTrue(client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get().get(3, TimeUnit.SECONDS).readAllBytes().length > 0);

            Rule ruleConfig = new Rule();
            ruleConfig.setType(RuleType.VALIDITY);
            ruleConfig.setConfig("NONE");
            client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).rules().post(ruleConfig);

            var executionException = Assertions.assertThrows(ExecutionException.class, () -> {
                client.admin().rules().post(ruleConfig).get(3, TimeUnit.SECONDS);
            });
            assertForbidden(executionException);
        } finally {
            client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).delete().get(3, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testAdminRole() throws Exception {
        var devAuth = new BaseBearerTokenAuthenticationProvider(new OidcAccessTokenProvider(authServerUrlConfigured, JWKSMockServer.ADMIN_CLIENT_ID, "test1"));
        RegistryClient client = createClient(devAuth);

        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();
        try {
            client.groups().byGroupId(groupId).artifacts().get().get(3, TimeUnit.SECONDS);
            ArtifactContent content = new ArtifactContent();
            content.setContent("{}");
            client.groups().byGroupId("ccc").artifacts().post(content, config -> {
                config.headers.add("X-Registry-ArtifactId", artifactId);
                config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
            }).get(3, TimeUnit.SECONDS);
            TestUtils.retry(() -> client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).meta().get().get(3, TimeUnit.SECONDS));
            assertTrue(client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get().get(3, TimeUnit.SECONDS).readAllBytes().length > 0);
            Rule ruleConfig = new Rule();
            ruleConfig.setType(RuleType.VALIDITY);
            ruleConfig.setConfig("NONE");
            client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).rules().post(ruleConfig);

            client.admin().rules().post(ruleConfig).get(3, TimeUnit.SECONDS);
        } finally {
            client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).delete().get(3, TimeUnit.SECONDS);
        }
    }
}