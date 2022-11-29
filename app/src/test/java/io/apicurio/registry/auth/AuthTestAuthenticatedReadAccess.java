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

/**
 * @author carnalca@redhat.com
 */

import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.AdminClient;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.v2.beans.ArtifactSearchResults;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.AuthTestProfileAuthenticatedReadAccess;
import io.apicurio.registry.utils.tests.JWKSMockServer;
import io.apicurio.rest.client.auth.Auth;
import io.apicurio.rest.client.auth.OidcAuth;
import io.apicurio.rest.client.auth.exception.AuthErrorHandler;
import io.apicurio.rest.client.auth.exception.ForbiddenException;
import io.apicurio.rest.client.spi.ApicurioHttpClient;
import io.apicurio.rest.client.spi.ApicurioHttpClientFactory;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@QuarkusTest
@TestProfile(AuthTestProfileAuthenticatedReadAccess.class)
@Tag(ApicurioTestTags.SLOW)
public class AuthTestAuthenticatedReadAccess extends AbstractResourceTestBase {

    @ConfigProperty(name = "registry.auth.token.endpoint")
    @Info(category = "auth", description = "Auth token endpoint", availableSince = "2.1.0.Final")
    String authServerUrl;

    final String groupId = getClass().getSimpleName() + "Group";

    ApicurioHttpClient httpClient;

    @Override
    protected RegistryClient createRestClientV2() {
        httpClient = ApicurioHttpClientFactory.create(authServerUrl, new AuthErrorHandler());
        Auth auth = new OidcAuth(httpClient, JWKSMockServer.ADMIN_CLIENT_ID, "test1");
        return this.createClient(auth);
    }

    @Override
    protected AdminClient createAdminClientV2() {
        httpClient = ApicurioHttpClientFactory.create(authServerUrl, new AuthErrorHandler());
        Auth auth = new OidcAuth(httpClient, JWKSMockServer.ADMIN_CLIENT_ID, "test1");
        return this.createAdminClient(auth);
    }

    @Test
    public void testReadOperationWithNoRole() throws Exception {
        // Read-only operation should work with credentials but no role.

        Auth auth = new OidcAuth(httpClient, JWKSMockServer.NO_ROLE_CLIENT_ID, "test1");
        RegistryClient client = this.createClient(auth);
        ArtifactSearchResults results = client.searchArtifacts(groupId, null, null, null, null, null, null, null, null);
        Assertions.assertTrue(results.getCount() >= 0);

        // Write operation should fail with credentials but not role.
        InputStream data = new ByteArrayInputStream(("{\r\n" +
                "    \"type\" : \"record\",\r\n" +
                "    \"name\" : \"userInfo\",\r\n" +
                "    \"namespace\" : \"my.example\",\r\n" +
                "    \"fields\" : [{\"name\" : \"age\", \"type\" : \"int\"}]\r\n" +
                "}").getBytes(StandardCharsets.UTF_8));
        Assertions.assertThrows(ForbiddenException.class, () -> {
            client.createArtifact(groupId, "testReadOperationWithNoRole", ArtifactType.AVRO, data);
        });
    }
}
