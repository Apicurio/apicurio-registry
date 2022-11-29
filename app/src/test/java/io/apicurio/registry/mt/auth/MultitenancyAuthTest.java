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

package io.apicurio.registry.mt.auth;

import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.AbstractRegistryTestBase;
import io.apicurio.registry.mt.MockTenantMetadataService;
import io.apicurio.registry.mt.MultitenancyNoAuthTest;
import io.apicurio.registry.rest.client.AdminClient;
import io.apicurio.registry.rest.client.AdminClientFactory;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.RegistryClientFactory;
import io.apicurio.registry.rest.client.exception.ArtifactNotFoundException;
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v2.beans.Rule;
import io.apicurio.registry.rest.v2.beans.SearchedArtifact;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.JWKSMockServer;
import io.apicurio.registry.utils.tests.MultitenancyAuthTestProfile;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.rest.client.auth.Auth;
import io.apicurio.rest.client.auth.BasicAuth;
import io.apicurio.rest.client.auth.OidcAuth;
import io.apicurio.rest.client.auth.exception.AuthErrorHandler;
import io.apicurio.rest.client.auth.exception.ForbiddenException;
import io.apicurio.rest.client.auth.exception.NotAuthorizedException;
import io.apicurio.rest.client.spi.ApicurioHttpClient;
import io.apicurio.rest.client.spi.ApicurioHttpClientFactory;
import io.apicurio.tenantmanager.api.datamodel.ApicurioTenant;
import io.apicurio.tenantmanager.api.datamodel.TenantStatusValue;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(MultitenancyAuthTestProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class MultitenancyAuthTest extends AbstractRegistryTestBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(MultitenancyNoAuthTest.class);

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    MockTenantMetadataService tenantMetadataService;

    @ConfigProperty(name = "registry.auth.token.endpoint")
    @Info(category = "auth", description = "Auth token endpoint", availableSince = "2.1.0.Final")
    String authServerUrlConfigured;

    @Test
    public void testMultitenantRegistry() throws Exception {

        if (!storage.supportsMultiTenancy()) {
            throw new TestAbortedException("Multitenancy not supported - aborting test");
        }

        String tenantId1 = UUID.randomUUID().toString();
        var tenant1 = new ApicurioTenant();
        tenant1.setTenantId(tenantId1);
        tenant1.setOrganizationId("aaa");
        tenant1.setStatus(TenantStatusValue.READY);
        tenantMetadataService.createTenant(tenant1);

        String tenantId2 = UUID.randomUUID().toString();
        var tenant2 = new ApicurioTenant();
        tenant2.setTenantId(tenantId2);
        tenant2.setOrganizationId("bbb");
        tenant2.setStatus(TenantStatusValue.READY);
        tenantMetadataService.createTenant(tenant2);

        String tenant1BaseUrl = "http://localhost:" + testPort + "/t/" + tenantId1;
        String tenant2BaseUrl = "http://localhost:" + testPort + "/t/" + tenantId2;

        Auth basicAutha = new BasicAuth(JWKSMockServer.BASIC_USER_A, JWKSMockServer.BASIC_PASSWORD);
        Auth basicAuthb = new BasicAuth(JWKSMockServer.BASIC_USER_B, JWKSMockServer.BASIC_PASSWORD);

        AdminClient adminClientTenant1 = AdminClientFactory.create(tenant1BaseUrl, Collections.emptyMap(), basicAutha);
        AdminClient adminClientTenant2 = AdminClientFactory.create(tenant2BaseUrl, Collections.emptyMap(), basicAuthb);

        RegistryClient clientTenant1 = RegistryClientFactory.create(tenant1BaseUrl, Collections.emptyMap(), basicAutha);
        RegistryClient clientTenant2 = RegistryClientFactory.create(tenant2BaseUrl, Collections.emptyMap(), basicAuthb);

        //set basic auth to false for this particular tenant
        clientTenant1.setConfigProperty("registry.auth.basic-auth-client-credentials.enabled", "false");
        Assertions.assertThrows(NotAuthorizedException.class, () -> tenantOperations(adminClientTenant1, clientTenant1));

        //Execute tenant2 operation, must pass since basic auth is enabled by default
        try {
            tenantOperations(adminClientTenant2, clientTenant2);
        } finally {
            cleanTenantArtifacts(clientTenant2);
        }

        //Since we have disabled basic auth, a client using bearer auth must be used to enable it back...
        ApicurioHttpClient httpClient = ApicurioHttpClientFactory.create(authServerUrlConfigured, new AuthErrorHandler());
        Auth bearerAuth = new OidcAuth(httpClient, JWKSMockServer.BASIC_USER_A, JWKSMockServer.BASIC_PASSWORD);

        RegistryClient clientWithBearerAuth = RegistryClientFactory.create(tenant1BaseUrl, Collections.emptyMap(), bearerAuth);
        clientWithBearerAuth.setConfigProperty("registry.auth.basic-auth-client-credentials.enabled", "true");

        //Once enabled back, basic auth can be used again.
        try {
            tenantOperations(adminClientTenant1, clientTenant1);
        } finally {
            cleanTenantArtifacts(clientTenant1);
        }

        //Finally, mix client when calling tenants, just to check that user from tenant a cannot access tenant b.
        AdminClient adminClientTenant1Authb = AdminClientFactory.create(tenant1BaseUrl, Collections.emptyMap(), basicAuthb);
        AdminClient adminClientTenant2Autha = AdminClientFactory.create(tenant2BaseUrl, Collections.emptyMap(), basicAutha);
        RegistryClient clientTenant1Authb = RegistryClientFactory.create(tenant1BaseUrl, Collections.emptyMap(), basicAuthb);
        RegistryClient clientTenant2Autha = RegistryClientFactory.create(tenant2BaseUrl, Collections.emptyMap(), basicAutha);

        Assertions.assertThrows(ForbiddenException.class, () -> tenantOperations(adminClientTenant1Authb, clientTenant1Authb));
        Assertions.assertThrows(ForbiddenException.class, () -> tenantOperations(adminClientTenant2Autha, clientTenant2Autha));
    }

    private void tenantOperations(AdminClient adminClient, RegistryClient client) throws Exception {
        //test apicurio api
        assertTrue(client.listArtifactsInGroup(null).getCount().intValue() == 0);

        String artifactId = TestUtils.generateArtifactId();
        ArtifactMetaData meta = client.createArtifact(null, artifactId, ArtifactType.JSON, new ByteArrayInputStream("{}".getBytes()));
        TestUtils.retry(() -> client.getContentByGlobalId(meta.getGlobalId()));

        assertNotNull(client.getLatestArtifact(meta.getGroupId(), meta.getId()));

        assertTrue(client.listArtifactsInGroup(null).getCount().intValue() == 1);

        Rule ruleConfig = new Rule();
        ruleConfig.setType(RuleType.VALIDITY);
        ruleConfig.setConfig("NONE");
        client.createArtifactRule(meta.getGroupId(), meta.getId(), ruleConfig);

        adminClient.createGlobalRule(ruleConfig);
    }

    private void cleanTenantArtifacts(RegistryClient client) throws Exception {
        List<SearchedArtifact> artifacts = client.listArtifactsInGroup(null).getArtifacts();
        for (SearchedArtifact artifact : artifacts) {
            try {
                client.deleteArtifact(artifact.getGroupId(), artifact.getId());
            } catch (ArtifactNotFoundException e) {
                //because of async storage artifact may be already deleted but listed anyway
                LOGGER.info(e.getMessage());
            } catch (Exception e) {
                LOGGER.error("", e);
            }
        }
        TestUtils.retry(() -> assertTrue(client.listArtifactsInGroup(null).getCount().intValue() == 0));
    }
}
