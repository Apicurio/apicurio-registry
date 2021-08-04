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
package io.apicurio.tests.multitenancy;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.multitenant.api.datamodel.NewRegistryTenantRequest;
import io.apicurio.multitenant.client.TenantManagerClient;
import io.apicurio.multitenant.client.TenantManagerClientImpl;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.RegistryClientFactory;
import io.apicurio.registry.rest.client.exception.ArtifactNotFoundException;
import io.apicurio.registry.rest.client.exception.ForbiddenException;
import io.apicurio.registry.rest.client.exception.NotAuthorizedException;
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v2.beans.RoleMapping;
import io.apicurio.registry.rest.v2.beans.Rule;
import io.apicurio.registry.rest.v2.beans.SearchedArtifact;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RoleType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.rest.client.auth.Auth;
import io.apicurio.rest.client.auth.OidcAuth;
import io.apicurio.tests.common.ApicurioRegistryBaseIT;
import io.apicurio.tests.common.Constants;
import io.apicurio.tests.common.RegistryFacade;
import io.apicurio.tests.common.auth.CustomJWTAuth;
import io.smallrye.jwt.build.Jwt;


/**
 * NOTE! This test is not testing security nor authentication features yet!
 * Once authentication is enabled in our deployments we will enable it for our testing
 *
 * @author Fabian Martinez
 */
@Tag(Constants.MULTITENANCY)
public class MultitenantAuthIT extends ApicurioRegistryBaseIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(MultitenantAuthIT.class);

    private RegistryFacade registryFacade = RegistryFacade.getInstance();

    @Test
    public void testSecuredMultitenantRegistry() throws Exception {
        RegistryClient clientTenant1 = createTenant();

        RegistryClient clientTenant2 = createTenant();

        try {
            performTenantAdminOperations(clientTenant1);
            try {
                performTenantAdminOperations(clientTenant2);
            } finally {
                cleanTenantArtifacts(clientTenant2);
            }
        } finally {
            cleanTenantArtifacts(clientTenant1);
        }
    }

    @Test
    public void testPermissions() throws Exception {

        var user1 = new TenantUser("eric", "cool-org");
        var user2 = new TenantUser("carles", "cool-org");

        //user1 is the owner of the tenant
        var tenantUrl = createTenant(user1);

        var user1Client = createUserClient(user1, tenantUrl);
        var user2Client = createUserClient(user2, tenantUrl);

        try {
            //user1 can access and automatically have admin permissions
            performTenantAdminOperations(user1Client);

            //user2 does not have access
            Assertions.assertThrows(ForbiddenException.class, () -> user2Client.listArtifactsInGroup(null));
            RoleMapping fooMapping = new RoleMapping();
            fooMapping.setPrincipalId("foo");
            fooMapping.setRole(RoleType.ADMIN);
            Assertions.assertThrows(ForbiddenException.class, () -> user2Client.createRoleMapping(fooMapping));
            Assertions.assertThrows(ForbiddenException.class, () -> user2Client.updateRoleMapping(user1.principalId, RoleType.READ_ONLY));

            //add read only permissions to user2
            RoleMapping mapping = new RoleMapping();
            mapping.setPrincipalId(user2.principalId);
            mapping.setRole(RoleType.READ_ONLY);
            user1Client.createRoleMapping(mapping);

            //verify read only permissions
            Assertions.assertThrows(ForbiddenException.class, () -> user2Client.createRoleMapping(mapping));
            Assertions.assertThrows(ForbiddenException.class, () -> user2Client.updateRoleMapping(user2.principalId, RoleType.ADMIN));
            performTenantReadOnlyOperations(user2Client, user1Client);

            //add developer role to user2
            user1Client.updateRoleMapping(user2.principalId, RoleType.DEVELOPER);

            //verify developer role permissions
            Assertions.assertThrows(ForbiddenException.class, () -> user2Client.createRoleMapping(mapping));
            Assertions.assertThrows(ForbiddenException.class, () -> user2Client.updateRoleMapping(user2.principalId, RoleType.ADMIN));
            performTenantDevRoleOperations(user2Client);

            //add admin role to user2
            user1Client.updateRoleMapping(user2.principalId, RoleType.ADMIN);

            //verify admin role permissions
            user2Client.createRoleMapping(fooMapping);
            user2Client.updateRoleMapping(fooMapping.getPrincipalId(), RoleType.ADMIN);
            performTenantAdminOperations(user2Client);

        } finally {
            cleanTenantArtifacts(user1Client);
        }
    }

    @Test
    public void testTenantAccess() throws Exception {

        var user1 = new TenantUser("eric", "org1");
        var tenant1Url = createTenant(user1);
        var tenant1User1Client = createUserClient(user1, tenant1Url);
        tenant1User1Client.listArtifactsInGroup(null);

        var user2 = new TenantUser("carles", "org2");
        var tenant2Url = createTenant(user2);
        var tenant2User2Client = createUserClient(user2, tenant2Url);
        tenant2User2Client.listArtifactsInGroup(null);

        //user2 cannot access user1 tenant, and viceversa
        Assertions.assertThrows(ForbiddenException.class, () -> createUserClient(user2, tenant1Url).listArtifactsInGroup(null));
        Assertions.assertThrows(ForbiddenException.class, () -> createUserClient(user1, tenant2Url).listArtifactsInGroup(null));

        //test invalid jwt
        var invalidClient = RegistryClientFactory.create(tenant1Url, Collections.emptyMap(), new Auth() {
            @Override
            public void apply(Map<String, String> requestHeaders) {
                var builder = Jwt.preferredUserName("foo")
                        .claim("rh_org_id", "my-org");

                String token = builder
                        .jws()
                        .keyId("247")
                        .sign();

                requestHeaders.put("Authorization", "Bearer " + token);
            }
        });
        Assertions.assertThrows(NotAuthorizedException.class, () -> invalidClient.listArtifactsInGroup(null));
    }

    private void performTenantAdminOperations(RegistryClient client) throws Exception {
        String groupId = TestUtils.generateGroupId();

        assertTrue(client.listArtifactsInGroup(groupId).getCount().intValue() == 0);

        String artifactId = TestUtils.generateArtifactId();
        ArtifactMetaData meta = client.createArtifact(groupId, artifactId, ArtifactType.JSON, new ByteArrayInputStream("{}".getBytes()));
        TestUtils.retry(() -> client.getContentByGlobalId(meta.getGlobalId()));

        assertNotNull(client.getLatestArtifact(meta.getGroupId(), meta.getId()));

        assertTrue(client.listArtifactsInGroup(groupId).getCount().intValue() == 1);

        Rule ruleConfig = new Rule();
        ruleConfig.setType(RuleType.VALIDITY);
        ruleConfig.setConfig("NONE");
        client.createArtifactRule(meta.getGroupId(), meta.getId(), ruleConfig);

        client.createGlobalRule(ruleConfig);
        client.deleteGlobalRule(RuleType.VALIDITY);
    }

    public void performTenantReadOnlyOperations(RegistryClient readOnlyClient, RegistryClient writePermissionsClient) throws Exception {

        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();
        assertTrue(readOnlyClient.listArtifactsInGroup(groupId).getCount().intValue() == 0);

        Assertions.assertThrows(ArtifactNotFoundException.class, () -> readOnlyClient.getArtifactMetaData(groupId, artifactId));
        Assertions.assertThrows(ArtifactNotFoundException.class, () -> readOnlyClient.getLatestArtifact("abc", artifactId));
        Assertions.assertThrows(ForbiddenException.class, () -> {
            readOnlyClient.createArtifact("ccc", artifactId, ArtifactType.JSON, new ByteArrayInputStream("{}".getBytes()));
        });
        Rule ruleConfig = new Rule();
        ruleConfig.setType(RuleType.VALIDITY);
        ruleConfig.setConfig("NONE");
        Assertions.assertThrows(ForbiddenException.class, () -> {
            readOnlyClient.createArtifactRule(groupId, artifactId, ruleConfig);
        });
        Assertions.assertThrows(ForbiddenException.class, () -> {
            readOnlyClient.createGlobalRule(ruleConfig);
        });
        Assertions.assertThrows(ForbiddenException.class, () -> readOnlyClient.listGlobalRules());

        ArtifactMetaData meta = writePermissionsClient.createArtifact(groupId, artifactId, ArtifactType.JSON, new ByteArrayInputStream("{}".getBytes()));
        TestUtils.retry(() -> writePermissionsClient.getArtifactMetaData(groupId, meta.getId()));

        assertNotNull(readOnlyClient.getLatestArtifact(groupId, artifactId));
    }

    public void performTenantDevRoleOperations(RegistryClient client) throws Exception {

        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();
        try {
            assertTrue(client.listArtifactsInGroup(groupId).getCount().intValue() == 0);

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
            Assertions.assertThrows(ForbiddenException.class, () -> client.listGlobalRules());
        } finally {
            client.deleteArtifact(groupId, artifactId);
        }
    }

    private void cleanTenantArtifacts(RegistryClient client) throws Exception {
        List<SearchedArtifact> artifacts = client.searchArtifacts(null, null, null, null, null, null, null, null, null).getArtifacts();
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
        TestUtils.retry(() -> assertTrue(client.searchArtifacts(null, null, null, null, null, null, null, null, null).getCount().intValue() == 0));
    }

    private RegistryClient createTenant() {
        TenantUser user = new TenantUser(UUID.randomUUID().toString(), UUID.randomUUID().toString());
        String tenantAppUrl = createTenant(user);
        return createUserClient(user, tenantAppUrl);
    }

    private String createTenant(TenantUser user) {

        String tenantId = UUID.randomUUID().toString();
        String tenantAppUrl = TestUtils.getRegistryBaseUrl() + "/t/" + tenantId;

        NewRegistryTenantRequest tenantReq = new NewRegistryTenantRequest();
        tenantReq.setOrganizationId(user.organizationId);
        tenantReq.setTenantId(tenantId);
        tenantReq.setCreatedBy(user.principalId);

        TenantManagerClient tenantManager = createTenantManagerClient();
        tenantManager.createTenant(tenantReq);

        return tenantAppUrl;
    }

    private RegistryClient createUserClient(TenantUser user, String tenantAppUrl) {
        return RegistryClientFactory.create(tenantAppUrl, Collections.emptyMap(), new CustomJWTAuth(user.principalId, user.organizationId));
    }

    private TenantManagerClient createTenantManagerClient() {
        var keycloak = registryFacade.getMTOnlyKeycloakMock();
        return new TenantManagerClientImpl(registryFacade.getTenantManagerUrl(), Collections.emptyMap(),
                new OidcAuth(keycloak.tokenEndpoint, keycloak.clientId, keycloak.clientSecret));
    }

}
