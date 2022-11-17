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

import io.apicurio.rest.client.auth.exception.ForbiddenException;
import io.apicurio.rest.client.auth.exception.NotAuthorizedException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.RegistryClientFactory;
import io.apicurio.registry.rest.client.exception.ArtifactNotFoundException;
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v2.beans.RoleMapping;
import io.apicurio.registry.rest.v2.beans.Rule;
import io.apicurio.registry.rest.v2.beans.SearchedArtifact;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RoleType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.rest.client.auth.Auth;
import io.apicurio.tests.common.ApicurioRegistryBaseIT;
import io.apicurio.tests.common.Constants;
import io.apicurio.tests.common.auth.CustomJWTAuth;
import io.smallrye.jwt.build.Jwt;


/**
 * @author Fabian Martinez
 */
@Tag(Constants.MULTITENANCY)
public class MultitenantAuthIT extends ApicurioRegistryBaseIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(MultitenantAuthIT.class);

    @Test
    public void testSecuredMultitenantRegistry() throws Exception {

        MultitenancySupport mt = new MultitenancySupport();

        RegistryClient clientTenant1 = mt.createTenant().client;

        RegistryClient clientTenant2 = mt.createTenant().client;

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

    /**
     * Tests two users in the same organization accessing the same tenant
     */
    @Test
    public void testPermissions() throws Exception {
        MultitenancySupport mt = new MultitenancySupport();

        String tenantId = UUID.randomUUID().toString();
        var user1 = new TenantUser(tenantId, "eric", "cool-org", UUID.randomUUID().toString());
        var user2 = new TenantUser(tenantId, "carles", "cool-org", UUID.randomUUID().toString());

        //user1 is the owner of the tenant
        TenantUserClient tenantOwner = mt.createTenant(user1);

        RegistryClient user2Client = mt.createUserClientCustomJWT(user2, tenantOwner.tenantAppUrl);

        try {
            //user1 can access and automatically have admin permissions
            performTenantAdminOperations(tenantOwner.client);

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
            tenantOwner.client.createRoleMapping(mapping);

            //verify read only permissions
            Assertions.assertThrows(ForbiddenException.class, () -> user2Client.createRoleMapping(mapping));
            Assertions.assertThrows(ForbiddenException.class, () -> user2Client.updateRoleMapping(user2.principalId, RoleType.ADMIN));
            performTenantReadOnlyOperations(user2Client, tenantOwner.client);

            //add developer role to user2
            tenantOwner.client.updateRoleMapping(user2.principalId, RoleType.DEVELOPER);

            //verify developer role permissions
            Assertions.assertThrows(ForbiddenException.class, () -> user2Client.createRoleMapping(mapping));
            Assertions.assertThrows(ForbiddenException.class, () -> user2Client.updateRoleMapping(user2.principalId, RoleType.ADMIN));
            performTenantDevRoleOperations(user2Client);

            //add admin role to user2
            tenantOwner.client.updateRoleMapping(user2.principalId, RoleType.ADMIN);

            //verify admin role permissions
            user2Client.createRoleMapping(fooMapping);
            user2Client.updateRoleMapping(fooMapping.getPrincipalId(), RoleType.ADMIN);
            performTenantAdminOperations(user2Client);

        } finally {
            cleanTenantArtifacts(tenantOwner.client);
        }
    }

    /**
     * Tests two users, each one in it's own organization, users should be able to access only tenants in it's own organization
     * @throws Exception
     */
    @Test
    public void testTenantAccess() throws Exception {

        MultitenancySupport mt = new MultitenancySupport();

        var user1 = new TenantUser(UUID.randomUUID().toString(), "eric", "org1", UUID.randomUUID().toString());
        TenantUserClient tenant1User1Client = mt.createTenant(user1);
        tenant1User1Client.client.listArtifactsInGroup(null);

        var user2 = new TenantUser(UUID.randomUUID().toString(), "carles", "org2", UUID.randomUUID().toString());
        TenantUserClient tenant2User2Client = mt.createTenant(user2);
        tenant2User2Client.client.listArtifactsInGroup(null);

        //user2 cannot access user1 tenant, and viceversa
        Assertions.assertThrows(ForbiddenException.class, () -> mt.createUserClient(user2, tenant1User1Client.tenantAppUrl).listArtifactsInGroup(null));
        Assertions.assertThrows(ForbiddenException.class, () -> mt.createUserClient(user1, tenant2User2Client.tenantAppUrl).listArtifactsInGroup(null));

        //test invalid jwt
        var invalidClient = RegistryClientFactory.create(tenant1User1Client.tenantAppUrl, Collections.emptyMap(), new Auth() {
            @Override
            public void apply(Map<String, String> requestHeaders) {
                var builder = Jwt.preferredUserName("foo")
                        .claim(CustomJWTAuth.RH_ORG_ID_CLAIM, "my-org");

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
        // Users with read access are allowed to list global rules, just not edit them.
        readOnlyClient.listGlobalRules();

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
            
            // The client can list the global rules but not edit them.  This should work.
            client.listGlobalRules();
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

}
