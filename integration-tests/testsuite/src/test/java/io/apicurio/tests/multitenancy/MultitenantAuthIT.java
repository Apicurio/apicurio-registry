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

import io.apicurio.tests.common.ApicurioRegistryBaseIT;
import io.apicurio.tests.common.Constants;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * @author Fabian Martinez
 */
@Tag(Constants.MULTITENANCY)
public class MultitenantAuthIT extends ApicurioRegistryBaseIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(MultitenantAuthIT.class);

    @Test
    @Disabled
    public void testSecuredMultitenantRegistry() throws Exception {

    }


//    private void performTenantAdminOperations(RegistryClient client) throws Exception {
//        assertTrue(client.listArtifactsInGroup(null).getCount().intValue() == 0);
//
//        String artifactId = TestUtils.generateArtifactId();
//        ArtifactMetaData meta = client.createArtifact(null, artifactId, ArtifactType.JSON, new ByteArrayInputStream("{}".getBytes()));
//        TestUtils.retry(() -> client.getContentByGlobalId(meta.getGlobalId()));
//
//        assertNotNull(client.getLatestArtifact(meta.getGroupId(), meta.getId()));
//
//        assertTrue(client.listArtifactsInGroup(null).getCount().intValue() == 1);
//
//        Rule ruleConfig = new Rule();
//        ruleConfig.setType(RuleType.VALIDITY);
//        ruleConfig.setConfig("NONE");
//        client.createArtifactRule(meta.getGroupId(), meta.getId(), ruleConfig);
//
//        client.createGlobalRule(ruleConfig);
//    }
//
//    private void cleanTenantArtifacts(RegistryClient client) throws Exception {
//        List<SearchedArtifact> artifacts = client.listArtifactsInGroup(null).getArtifacts();
//        for (SearchedArtifact artifact : artifacts) {
//            try {
//                client.deleteArtifact(artifact.getGroupId(), artifact.getId());
//            } catch (ArtifactNotFoundException e) {
//                //because of async storage artifact may be already deleted but listed anyway
//                LOGGER.info(e.getMessage());
//            } catch (Exception e) {
//                LOGGER.error("", e);
//            }
//        }
//        TestUtils.retry(() -> assertTrue(client.listArtifactsInGroup(null).getCount().intValue() == 0));
//    }
//
//    private RegistryClient createAndGetTenantAdminClient() {
//        TenantAuthInfo tenantInfo = createTenant();
//        Auth auth = new KeycloakAuth(registryFacade.getAuthServerUrl(), tenantInfo.getRealm(), tenantInfo.getClientId(), tenantInfo.getClientSecret());
//        RegistryClient client  = RegistryClientFactory.create(tenantInfo.getTenantAppUrl() + "/apis/registry/v2", Collections.emptyMap(), auth);
//        return client;
//    }
//
//    private TenantAuthInfo createTenant() {
//        Keycloak keycloak = registryFacade.getKeycloakAdminClient();
//
//        String tenantId = UUID.randomUUID().toString();
//        String tenantAppUrl = TestUtils.getRegistryBaseUrl() + "/t/" + tenantId;
//
//        TenantAuthInfo tenantInfo = createTenantAuthResources(keycloak, registryFacade.getAuthServerUrl(), tenantId, tenantAppUrl);
//
//        tenantInfo.setTenantAppUrl(tenantAppUrl);
//
//        NewRegistryTenantRequest tenantReq = new NewRegistryTenantRequest();
//        tenantReq.setOrganizationId("foo");
//        tenantReq.setTenantId(tenantId);
//
//        TenantManagerClient tenantManager = new TenantManagerClientImpl(registryFacade.getTenantManagerUrl());
//        tenantManager.createTenant(tenantReq);
//
//        return tenantInfo;
//    }
//
//    public TenantAuthInfo createTenantAuthResources(Keycloak keycloak, String authServerUrl, String tenantId, String registryAppUrl) {
//
//        final RealmRepresentation realmRepresentation = new RealmRepresentation();
//        final String realmTenantId = "realm".concat("-").concat(tenantId);
//
//        realmRepresentation.setDisplayName(realmTenantId);
//        realmRepresentation.setRealm(realmTenantId);
//        realmRepresentation.setEnabled(true);
//
//        realmRepresentation.setRoles(buildRealmRoles());
//
//        final ClientRepresentation client = new ClientRepresentation();
//        client.setEnabled(true);
//        client.setClientId(tenantId);
//        client.setName("client-" + tenantId);
//        client.setRedirectUris(List.of("*"));
////        client.setPublicClient(true);
//        client.setServiceAccountsEnabled(true);
//        client.setDirectAccessGrantsEnabled(true);
//        client.setSecret(tenantId + "-secret");
//        client.setClientAuthenticatorType("client-secret");
//
//        realmRepresentation.setClients(List.of(client));
//
//        UserRepresentation user = new UserRepresentation();
//        user.setUsername("service-account-" + tenantId);
//        user.setEnabled(true);
//        user.setServiceAccountClientId(client.getClientId());
//        user.setRealmRoles(List.of(adminRole));
//
//        realmRepresentation.setUsers(Collections.singletonList(user));
//
//        keycloak.realms().create(realmRepresentation);
//
//        TenantAuthInfo info = new TenantAuthInfo();
//        info.setRealm(realmRepresentation.getRealm());
//        info.setRealmAuthServerUrl(String.format("%s/realms/%s", authServerUrl, realmRepresentation.getRealm()));
//        info.setClientId(client.getClientId());
//        info.setClientSecret(client.getSecret());
//        return info;
//    }
//
//    private RolesRepresentation buildRealmRoles() {
//
//        final RolesRepresentation rolesRepresentation = new RolesRepresentation();
//
//        final List<RoleRepresentation> newRealmRoles = Stream.of(tenantRoles)
//                .map(r -> {
//                    RoleRepresentation rp = new RoleRepresentation();
//                    rp.setName(r);
//                    return rp;
//                })
//                .collect(Collectors.toList());
//
//        rolesRepresentation.setRealm(newRealmRoles);
//
//        return rolesRepresentation;
//    }

}
