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



import com.microsoft.kiota.authentication.BaseBearerTokenAuthenticationProvider;
import com.microsoft.kiota.http.OkHttpRequestAdapter;
import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.AuthTestProfileAuthenticatedReadAccess;
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
@TestProfile(AuthTestProfileAuthenticatedReadAccess.class)
@Tag(ApicurioTestTags.SLOW)
public class AuthTestAuthenticatedReadAccess extends AbstractResourceTestBase {

    @ConfigProperty(name = "registry.auth.token.endpoint")
    @Info(category = "auth", description = "Auth token endpoint", availableSince = "2.1.0.Final")
    String authServerUrl;

    final String groupId = getClass().getSimpleName() + "Group";

    @Override
    protected RegistryClient createRestClientV2() {
        var adapter = new OkHttpRequestAdapter(
                new BaseBearerTokenAuthenticationProvider(
                        new OidcAccessTokenProvider(authServerUrl, JWKSMockServer.ADMIN_CLIENT_ID, "test1")));
        adapter.setBaseUrl(registryV2ApiUrl);
        return new RegistryClient(adapter);
    }

    @Test
    public void testReadOperationWithNoRole() throws Exception {
        // Read-only operation should work with credentials but no role.
        var adapter = new OkHttpRequestAdapter(
                new BaseBearerTokenAuthenticationProvider(
                        new OidcAccessTokenProvider(authServerUrl, JWKSMockServer.NO_ROLE_CLIENT_ID, "test1")));
        adapter.setBaseUrl(registryV2ApiUrl);
        RegistryClient client = new RegistryClient(adapter);
        var results = client.search().artifacts().get(config -> config.queryParameters.group = groupId).get(3, TimeUnit.SECONDS);
        Assertions.assertTrue(results.getCount() >= 0);

        // Write operation should fail with credentials but not role.
        String data = "{\r\n" +
                "    \"type\" : \"record\",\r\n" +
                "    \"name\" : \"userInfo\",\r\n" +
                "    \"namespace\" : \"my.example\",\r\n" +
                "    \"fields\" : [{\"name\" : \"age\", \"type\" : \"int\"}]\r\n" +
                "}";
        var executionException = Assertions.assertThrows(ExecutionException.class, () -> {
            var content = new io.apicurio.registry.rest.client.models.ArtifactContent();
            content.setContent(data);
            client
                    .groups()
                    .byGroupId(groupId)
                    .artifacts()
                    .post(content, config -> {
                        config.headers.add("X-Registry-ArtifactType", ArtifactType.AVRO);
                        config.headers.add("X-Registry-ArtifactId", "testReadOperationWithNoRole");
                    }).get(3, TimeUnit.SECONDS);
        });
        assertForbidden(executionException);
    }
}
