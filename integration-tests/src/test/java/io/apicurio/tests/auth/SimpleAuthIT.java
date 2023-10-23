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
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.ArtifactContent;
import io.apicurio.registry.rest.client.models.ArtifactMetaData;
import io.apicurio.registry.rest.client.models.Rule;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rest.client.models.UserInfo;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.tests.AuthTestProfile;
import io.apicurio.registry.utils.tests.JWKSMockServer;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.apicurio.tests.utils.Constants;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Carles Arnal
 */
@Tag(Constants.AUTH)
@TestProfile(AuthTestProfile.class)
@QuarkusIntegrationTest
public class SimpleAuthIT extends ApicurioRegistryBaseIT {

    final String groupId = "authTestGroupId";

    private static final ArtifactContent content = new ArtifactContent();

    static {
        content.setContent("{}");
    }

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
        adapter.setBaseUrl(getRegistryV2ApiUrl());
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
        var adapter = new OkHttpRequestAdapter(
                new BaseBearerTokenAuthenticationProvider(
                        new OidcAccessTokenProvider(authServerUrlConfigured, JWKSMockServer.READONLY_CLIENT_ID, "test1")));
        adapter.setBaseUrl(getRegistryV2ApiUrl());
        RegistryClient client = new RegistryClient(adapter);
        String artifactId = TestUtils.generateArtifactId();
        client.groups().byGroupId(groupId).artifacts().get().get(3, TimeUnit.SECONDS);
        var executionException1 = Assertions.assertThrows(ExecutionException.class, () -> {
            client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).meta().get().get(3, TimeUnit.SECONDS);
        });
        assertArtifactNotFound(executionException1);
        var executionException2 = Assertions.assertThrows(ExecutionException.class, () -> {
            client.groups().byGroupId("abc").artifacts().byArtifactId(artifactId).get().get(3, TimeUnit.SECONDS);
        });
        assertArtifactNotFound(executionException2);
        var executionException3 = Assertions.assertThrows(ExecutionException.class, () -> {
            client.groups().byGroupId("testReadOnly").artifacts().post(content, config -> {
                config.headers.add("X-Registry-ArtifactId", artifactId);
                config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
            }).get(3, TimeUnit.SECONDS);
        });
        assertForbidden(executionException3);

        var devAdapter = new OkHttpRequestAdapter(
                new BaseBearerTokenAuthenticationProvider(
                        new OidcAccessTokenProvider(authServerUrlConfigured, JWKSMockServer.DEVELOPER_CLIENT_ID, "test1")));
        devAdapter.setBaseUrl(getRegistryV2ApiUrl());
        RegistryClient devClient = new RegistryClient(devAdapter);

        ArtifactMetaData meta = devClient.groups().byGroupId(groupId).artifacts().post(content, config -> {
            config.headers.add("X-Registry-ArtifactId", artifactId);
            config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
        }).get(3, TimeUnit.SECONDS);

        TestUtils.retry(() -> devClient.groups().byGroupId(groupId).artifacts().byArtifactId(meta.getId()).meta().get().get(3, TimeUnit.SECONDS));

        assertNotNull(client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get().get(3, TimeUnit.SECONDS));

        UserInfo userInfo = client.users().me().get().get(3, TimeUnit.SECONDS);
        assertNotNull(userInfo);
        Assertions.assertEquals("service-account-readonly-client", userInfo.getUsername());
        Assertions.assertFalse(userInfo.getAdmin());
        Assertions.assertFalse(userInfo.getDeveloper());
        Assertions.assertTrue(userInfo.getViewer());
    }

    @Test
    public void testDevRole() throws Exception {
        var adapter = new OkHttpRequestAdapter(
                new BaseBearerTokenAuthenticationProvider(
                        new OidcAccessTokenProvider(authServerUrlConfigured, JWKSMockServer.DEVELOPER_CLIENT_ID, "test1")));
        adapter.setBaseUrl(getRegistryV2ApiUrl());
        RegistryClient client = new RegistryClient(adapter);
        String artifactId = TestUtils.generateArtifactId();
        try {
            client.groups().byGroupId(groupId).artifacts().get().get(3, TimeUnit.SECONDS);

            client.groups().byGroupId(groupId).artifacts().post(content, config -> {
                config.headers.add("X-Registry-ArtifactId", artifactId);
                config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
            }).get(3, TimeUnit.SECONDS);
            TestUtils.retry(() -> client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).meta().get().get(3, TimeUnit.SECONDS));

            Assertions.assertTrue(client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get().get(3, TimeUnit.SECONDS).readAllBytes().length > 0);

            Rule ruleConfig = new Rule();
            ruleConfig.setType(RuleType.VALIDITY);
            ruleConfig.setConfig(ValidityLevel.NONE.name());
            client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).rules().post(ruleConfig).get(3, TimeUnit.SECONDS);

            var executionException = Assertions.assertThrows(ExecutionException.class, () -> {
                client.admin().rules().post(ruleConfig).get(3, TimeUnit.SECONDS);
            });
            assertForbidden(executionException);

            UserInfo userInfo = client.users().me().get().get(3, TimeUnit.SECONDS);
            assertNotNull(userInfo);
            Assertions.assertEquals("service-account-developer-client", userInfo.getUsername());
            Assertions.assertFalse(userInfo.getAdmin());
            Assertions.assertTrue(userInfo.getDeveloper());
            Assertions.assertFalse(userInfo.getViewer());
        } finally {
            client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).delete().get(3, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testAdminRole() throws Exception {
        var adapter = new OkHttpRequestAdapter(
                new BaseBearerTokenAuthenticationProvider(
                        new OidcAccessTokenProvider(authServerUrlConfigured, JWKSMockServer.ADMIN_CLIENT_ID, "test1")));
        adapter.setBaseUrl(getRegistryV2ApiUrl());
        RegistryClient client = new RegistryClient(adapter);
        String artifactId = TestUtils.generateArtifactId();
        try {
            client.groups().byGroupId(groupId).artifacts().get().get(3, TimeUnit.SECONDS);

            client.groups().byGroupId(groupId).artifacts().post(content, config -> {
                config.headers.add("X-Registry-ArtifactId", artifactId);
                config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
            }).get(3, TimeUnit.SECONDS);
            TestUtils.retry(() -> client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).meta().get().get(3, TimeUnit.SECONDS));

            Assertions.assertTrue(client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get().get(3, TimeUnit.SECONDS).readAllBytes().length > 0);

            Rule ruleConfig = new Rule();
            ruleConfig.setType(RuleType.VALIDITY);
            ruleConfig.setConfig(ValidityLevel.NONE.name());
            client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).rules().post(ruleConfig).get(3, TimeUnit.SECONDS);

            client.admin().rules().post(ruleConfig).get(3, TimeUnit.SECONDS);

            UserInfo userInfo = client.users().me().get().get(3, TimeUnit.SECONDS);
            assertNotNull(userInfo);
            Assertions.assertEquals("service-account-admin-client", userInfo.getUsername());
            Assertions.assertTrue(userInfo.getAdmin());
            Assertions.assertFalse(userInfo.getDeveloper());
            Assertions.assertFalse(userInfo.getViewer());
        } finally {
            client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).delete().get(3, TimeUnit.SECONDS);
        }
    }

    protected void assertArtifactNotFound(ExecutionException executionException) {
        Assertions.assertNotNull(executionException.getCause());
        Assertions.assertEquals(io.apicurio.registry.rest.client.models.Error.class, executionException.getCause().getClass());
        Assertions.assertEquals("ArtifactNotFoundException", ((io.apicurio.registry.rest.client.models.Error) executionException.getCause()).getName());
        Assertions.assertEquals(404, ((io.apicurio.registry.rest.client.models.Error) executionException.getCause()).getErrorCode());
    }
}