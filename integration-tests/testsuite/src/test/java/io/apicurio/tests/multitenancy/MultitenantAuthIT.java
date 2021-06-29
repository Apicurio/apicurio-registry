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
import java.util.UUID;
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
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v2.beans.Rule;
import io.apicurio.registry.rest.v2.beans.SearchedArtifact;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.common.ApicurioRegistryBaseIT;
import io.apicurio.tests.common.Constants;
import io.apicurio.tests.common.RegistryFacade;


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


    private void performTenantAdminOperations(RegistryClient client) throws Exception {
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

        client.createGlobalRule(ruleConfig);
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

    private RegistryClient createTenant() {

        String tenantId = UUID.randomUUID().toString();
        String tenantAppUrl = TestUtils.getRegistryBaseUrl() + "/t/" + tenantId;

        NewRegistryTenantRequest tenantReq = new NewRegistryTenantRequest();
        tenantReq.setOrganizationId("foo");
        tenantReq.setTenantId(tenantId);

        TenantManagerClient tenantManager = new TenantManagerClientImpl(registryFacade.getTenantManagerUrl());
        tenantManager.createTenant(tenantReq);

        return RegistryClientFactory.create(tenantAppUrl, Collections.emptyMap(), null);
    }

}
