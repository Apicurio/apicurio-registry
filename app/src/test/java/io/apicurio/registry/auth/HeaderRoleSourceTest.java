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

package io.apicurio.registry.auth;

import com.microsoft.kiota.authentication.BaseBearerTokenAuthenticationProvider;
import com.microsoft.kiota.http.OkHttpRequestAdapter;
import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.AuthTestProfileWithHeaderRoles;
import io.apicurio.registry.utils.tests.JWKSMockServer;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;


@QuarkusTest
@TestProfile(AuthTestProfileWithHeaderRoles.class)
@Tag(ApicurioTestTags.SLOW)
public class HeaderRoleSourceTest extends AbstractResourceTestBase {

    private static final String TEST_CONTENT = "{\r\n" +
            "    \"type\" : \"record\",\r\n" +
            "    \"name\" : \"userInfo\",\r\n" +
            "    \"namespace\" : \"my.example\",\r\n" +
            "    \"fields\" : [{\"name\" : \"age\", \"type\" : \"int\"}]\r\n" +
            "} ";

    @ConfigProperty(name = "registry.auth.token.endpoint")
    @Info(category = "auth", description = "Auth token endpoint", availableSince = "2.1.0.Final")
    String authServerUrlConfigured;

    @Override
    protected RegistryClient createRestClientV2() {
        var adapter = new OkHttpRequestAdapter(
                new BaseBearerTokenAuthenticationProvider(
                        new OidcAccessTokenProvider(authServerUrlConfigured, JWKSMockServer.ADMIN_CLIENT_ID, "test1")));
        adapter.setBaseUrl(registryV2ApiUrl);
        return new RegistryClient(adapter);
    }

    @Test
    public void testLocalRoles() throws Exception {
        var content = new io.apicurio.registry.rest.client.models.ArtifactContent();
        content.setContent(TEST_CONTENT);

        var rule = new io.apicurio.registry.rest.client.models.Rule();
        rule.setConfig(ValidityLevel.FULL.name());
        rule.setType(io.apicurio.registry.rest.client.models.RuleType.VALIDITY);

        var noRoleAdapter = new OkHttpRequestAdapter(
                new BaseBearerTokenAuthenticationProvider(
                        new OidcAccessTokenProvider(authServerUrlConfigured, JWKSMockServer.NO_ROLE_CLIENT_ID, "test1")));
        noRoleAdapter.setBaseUrl(registryV2ApiUrl);
        var noRoleClient = new RegistryClient(noRoleAdapter);

        var readAdapter = new OkHttpRequestAdapter(
                new BaseBearerTokenAuthenticationProvider(
                        new OidcAccessTokenProvider(authServerUrlConfigured, JWKSMockServer.READONLY_CLIENT_ID, "test1")));
        readAdapter.setBaseUrl(registryV2ApiUrl);
        var readClient = new RegistryClient(readAdapter);

        var devAdapter = new OkHttpRequestAdapter(
                new BaseBearerTokenAuthenticationProvider(
                        new OidcAccessTokenProvider(authServerUrlConfigured, JWKSMockServer.DEVELOPER_CLIENT_ID, "test1")));
        devAdapter.setBaseUrl(registryV2ApiUrl);
        var devClient = new RegistryClient(devAdapter);

        var adminAdapter = new OkHttpRequestAdapter(
                new BaseBearerTokenAuthenticationProvider(
                        new OidcAccessTokenProvider(authServerUrlConfigured, JWKSMockServer.ADMIN_CLIENT_ID, "test1")));
        adminAdapter.setBaseUrl(registryV2ApiUrl);
        var adminClient = new RegistryClient(adminAdapter);


        // User is authenticated but no roles assigned - operations should fail.
        var executionException1 = Assertions.assertThrows(ExecutionException.class, () -> {
            noRoleClient.groups().byGroupId("default").artifacts().get().get(3, TimeUnit.SECONDS);
        });
        assertForbidden(executionException1);

        var executionException2 = Assertions.assertThrows(ExecutionException.class, () -> {
            noRoleClient
                .groups()
                .byGroupId(UUID.randomUUID().toString())
                .artifacts()
                .post(content, config -> {
                    config.headers.add("X-Registry-ArtifactId", getClass().getSimpleName());
                }).get(3, TimeUnit.SECONDS);
        });
        assertForbidden(executionException2);

        var executionException3 = Assertions.assertThrows(ExecutionException.class, () -> {
            noRoleClient.admin().rules().post(rule).get(3, TimeUnit.SECONDS);
        });
        assertForbidden(executionException3);


        // Now using the read client user should be able to read but nothing else
        readClient.groups().byGroupId("default").artifacts().get(config -> {
            config.headers.add("X-Registry-Role", "sr-readonly");
        }).get(3, TimeUnit.SECONDS);
        var executionException4 = Assertions.assertThrows(ExecutionException.class, () -> {
            readClient
                    .groups()
                    .byGroupId(UUID.randomUUID().toString())
                    .artifacts()
                    .post(content, config -> {
                        config.headers.add("X-Registry-ArtifactId", getClass().getSimpleName());
                        config.headers.add("X-Registry-Role", "sr-readonly");
                    }).get(3, TimeUnit.SECONDS);
        });
        assertForbidden(executionException4);

        var executionException5 = Assertions.assertThrows(ExecutionException.class, () -> {
            readClient.admin().rules().post(rule, config -> {
                config.headers.add("X-Registry-Role", "sr-readonly");
            }).get(3, TimeUnit.SECONDS);
        });
        assertForbidden(executionException5);

        // the user can read and write with the developer client but not admin
        devClient.groups().byGroupId("default").artifacts().get(config -> {
            config.headers.add("X-Registry-Role", "sr-developer");
        }).get(3, TimeUnit.SECONDS);
        devClient
            .groups()
            .byGroupId(UUID.randomUUID().toString())
            .artifacts()
            .post(content, config -> {
                config.headers.add("X-Registry-ArtifactId", getClass().getSimpleName());
                config.headers.add("X-Registry-Role", "sr-developer");
            }).get(3, TimeUnit.SECONDS);
        var executionException6 = Assertions.assertThrows(ExecutionException.class, () -> {
            devClient.admin().rules().post(rule, config -> {
                config.headers.add("X-Registry-Role", "sr-developer");
            }).get(3, TimeUnit.SECONDS);
        });
        assertForbidden(executionException6);

        // the user can do everything with the admin client
        adminClient.groups().byGroupId("default").artifacts().get(config -> {
            config.headers.add("X-Registry-Role", "sr-admin");
        }).get(3, TimeUnit.SECONDS);
        adminClient
                .groups()
                .byGroupId(UUID.randomUUID().toString())
                .artifacts()
                .post(content, config -> {
                    config.headers.add("X-Registry-ArtifactId", getClass().getSimpleName());
                    config.headers.add("X-Registry-Role", "sr-admin");
                }).get(3, TimeUnit.SECONDS);
        adminClient.admin().rules().post(rule, config -> {
            config.headers.add("X-Registry-Role", "sr-admin");
        }).get(3, TimeUnit.SECONDS);
    }
}
