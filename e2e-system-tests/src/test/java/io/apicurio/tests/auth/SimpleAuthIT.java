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

import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.apicurio.tests.utils.Constants;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.RegistryClientFactory;
import io.apicurio.registry.rest.client.exception.ArtifactNotFoundException;
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v2.beans.Rule;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.tests.AuthTestProfile;
import io.apicurio.registry.utils.tests.JWKSMockServer;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.rest.client.auth.Auth;
import io.apicurio.rest.client.auth.OidcAuth;
import io.apicurio.rest.client.auth.exception.AuthErrorHandler;
import io.apicurio.rest.client.auth.exception.ForbiddenException;
import io.apicurio.rest.client.auth.exception.NotAuthorizedException;
import io.apicurio.rest.client.spi.ApicurioHttpClient;
import io.apicurio.rest.client.spi.ApicurioHttpClientFactory;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Carles Arnal
 */
@Tag(Constants.AUTH)
@TestProfile(AuthTestProfile.class)
@QuarkusIntegrationTest
public class SimpleAuthIT extends ApicurioRegistryBaseIT {

    ApicurioHttpClient httpClient;

    protected ApicurioHttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = ApicurioHttpClientFactory.create(authServerUrlConfigured, new AuthErrorHandler());
        }
        return httpClient;
    }

    @Override
    public void cleanArtifacts() throws Exception {
        //Don't clean
    }

    @Override
    protected RegistryClient createRegistryClient() {
        Auth auth = new OidcAuth(getHttpClient(), JWKSMockServer.ADMIN_CLIENT_ID, "test1");
        return createClient(auth);
    }

    private RegistryClient createClient(Auth auth) {
        return RegistryClientFactory.create(getRegistryBaseUrl(), Collections.emptyMap(), auth);
    }

    @Test
    public void testWrongCreds() throws Exception {
        Auth auth = new OidcAuth(getHttpClient(), JWKSMockServer.WRONG_CREDS_CLIENT_ID, "test55");
        RegistryClient client = createClient(auth);
        Assertions.assertThrows(NotAuthorizedException.class, () -> {
            client.listArtifactsInGroup("foo");
        });
    }

    @Test
    public void testReadOnly() throws Exception {
        Auth auth = new OidcAuth(getHttpClient(), JWKSMockServer.READONLY_CLIENT_ID, "test1");
        RegistryClient client = createClient(auth);

        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();
        client.listArtifactsInGroup(groupId);
        Assertions.assertThrows(ArtifactNotFoundException.class, () -> client.getArtifactMetaData(groupId, artifactId));
        Assertions.assertThrows(ArtifactNotFoundException.class, () -> client.getLatestArtifact("abc", artifactId));
        Assertions.assertThrows(ForbiddenException.class, () -> {
            client.createArtifact("ccc", artifactId, ArtifactType.JSON, new ByteArrayInputStream("{}".getBytes()));
        });
        {
            Auth devAuth = new OidcAuth(getHttpClient(), JWKSMockServer.DEVELOPER_CLIENT_ID, "test1");
            RegistryClient devClient = createClient(devAuth);
            ArtifactMetaData meta = devClient.createArtifact(groupId, artifactId, ArtifactType.JSON, new ByteArrayInputStream("{}".getBytes()));
            TestUtils.retry(() -> devClient.getArtifactMetaData(groupId, meta.getId()));
        }
        assertNotNull(client.getLatestArtifact(groupId, artifactId));
    }

    @Test
    public void testDevRole() throws Exception {
        Auth devAuth = new OidcAuth(getHttpClient(), JWKSMockServer.DEVELOPER_CLIENT_ID, "test1");
        RegistryClient client = createClient(devAuth);

        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();
        try {
            client.listArtifactsInGroup(groupId);

            client.createArtifact(groupId, artifactId, ArtifactType.JSON, new ByteArrayInputStream("{}".getBytes()));
            TestUtils.retry(() -> client.getArtifactMetaData(groupId, artifactId));

            assertNotNull(client.getLatestArtifact(groupId, artifactId));

            Rule ruleConfig = new Rule();
            ruleConfig.setType(RuleType.VALIDITY);
            ruleConfig.setConfig("NONE");
            client.createArtifactRule(groupId, artifactId, ruleConfig);

            Assertions.assertThrows(ForbiddenException.class, () -> {
                client.createGlobalRule(ruleConfig);
            });
        } finally {
            client.deleteArtifact(groupId, artifactId);
        }
    }

    @Test
    public void testAdminRole() throws Exception {
        Auth auth = new OidcAuth(getHttpClient(), JWKSMockServer.ADMIN_CLIENT_ID, "test1");
        RegistryClient client = createClient(auth);

        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();
        try {
            client.listArtifactsInGroup(groupId);
            client.createArtifact(groupId, artifactId, ArtifactType.JSON, new ByteArrayInputStream("{}".getBytes()));
            TestUtils.retry(() -> client.getArtifactMetaData(groupId, artifactId));
            assertNotNull(client.getLatestArtifact(groupId, artifactId));
            Rule ruleConfig = new Rule();
            ruleConfig.setType(RuleType.VALIDITY);
            ruleConfig.setConfig("NONE");
            client.createArtifactRule(groupId, artifactId, ruleConfig);

            client.createGlobalRule(ruleConfig);
        } finally {
            client.deleteArtifact(groupId, artifactId);
        }
    }
}