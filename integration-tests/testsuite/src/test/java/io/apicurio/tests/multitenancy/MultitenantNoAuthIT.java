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

import io.apicurio.multitenant.api.datamodel.NewRegistryTenantRequest;
import io.apicurio.multitenant.api.datamodel.RegistryTenant;
import io.apicurio.multitenant.api.datamodel.TenantStatusValue;
import io.apicurio.multitenant.api.datamodel.UpdateRegistryTenantRequest;
import io.apicurio.multitenant.client.TenantManagerClient;
import io.apicurio.multitenant.client.TenantManagerClientImpl;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.RegistryClientFactory;
import io.apicurio.registry.rest.client.exception.RestClientException;
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.common.ApicurioRegistryBaseIT;
import io.apicurio.tests.common.Constants;
import io.apicurio.tests.common.RegistryFacade;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Fabian Martinez
 * @author Jakub Senko <jsenko@redhat.com>
 */
@Tag(Constants.MULTITENANCY)
public class MultitenantNoAuthIT extends ApicurioRegistryBaseIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(MultitenantNoAuthIT.class);

    private static RegistryFacade registryFacade = RegistryFacade.getInstance();

    private static TenantManagerClient tenantManager = new TenantManagerClientImpl(registryFacade.getTenantManagerUrl());

    private static String groupId = "testGroup";

    @Test
    public void testTenantReaper() throws Exception {
        List<RegistryTenant> tenants = new ArrayList<>(55);

        // Create 55 tenants to force use of pagination (currently 50), and some data
        for (int i = 0; i < 55; i++) {
            RegistryTenant tenant = createTenant();
            tenants.add(tenant);
            RegistryClient client = createClientForTenant(tenant.getTenantId());
            createSomeArtifact(client);
            createSomeArtifact(client);
            Assertions.assertEquals(2, client.listArtifactsInGroup(groupId).getCount());
        }

        // Mark 53 tenants for deletion
        for (int i = 0; i < 53; i++) {
            updateTenantStatus(tenants.get(i), TenantStatusValue.TO_BE_DELETED);
        }

        // Wait for the reaper
        TestUtils.waitFor("tenant reaper", 3000, 6 * 3000, () -> {
            for (int i = 0; i < 53; i++) {
                if (tenantManager.getTenant(tenants.get(i).getTenantId()).getStatus() != TenantStatusValue.DELETED) {
                    return false;
                }
            }
            return true;
        });

        // Ensure that the APIs are disabled
        for (int i = 0; i < 53; i++) {
            RegistryClient client = createClientForTenant(tenants.get(i).getTenantId());
            try {
                client.listArtifactsInGroup(groupId);
                Assertions.fail("This request should not succeed");
            } catch (RestClientException ex) {
                // OK
                Assertions.assertEquals(404, ex.getError().getErrorCode());
            }
        }

        // To test that the data was removed, we will change the tenant status back to ready
        // TODO Make sure this is safe to do in the future
        for (int i = 0; i < 53; i++) {
            updateTenantStatus(tenants.get(i), TenantStatusValue.READY);
        }

        // Wait for the reaper again, because it also purges the tenant loader cache
        TestUtils.waitFor("tenant reaper 2", 3000, 6 * 3000, () -> {
            RegistryClient client = createClientForTenant(tenants.get(0).getTenantId());
            try {
                client.listArtifactsInGroup(groupId);
                return true; // The API is available again
            } catch (RestClientException ex) {
                return false;
            }
        });

        // First 53 tenants should be "empty" and the last 2 should keep their content
        for (int i = 0; i < 53; i++) {
            RegistryClient client = createClientForTenant(tenants.get(i).getTenantId());
            client.listArtifactsInGroup(groupId);
            Assertions.assertEquals(0, client.listArtifactsInGroup(groupId).getCount());
        }
        Assertions.assertEquals(2, createClientForTenant(tenants.get(53).getTenantId()).listArtifactsInGroup(groupId).getCount());
        Assertions.assertEquals(2, createClientForTenant(tenants.get(54).getTenantId()).listArtifactsInGroup(groupId).getCount());
    }

    private void createSomeArtifact(RegistryClient client) throws Exception {
        String artifactId = TestUtils.generateArtifactId();
        ArtifactMetaData meta = client.createArtifact(groupId, artifactId, ArtifactType.JSON, new ByteArrayInputStream("{}".getBytes()));
        TestUtils.retry(() -> client.getContentByGlobalId(meta.getGlobalId()));
        assertNotNull(client.getLatestArtifact(meta.getGroupId(), meta.getId()));
    }

    private RegistryClient createClientForTenant(String tenantId) {
        String tenantAppUrl = TestUtils.getRegistryBaseUrl() + "/t/" + tenantId;
        return RegistryClientFactory.create(tenantAppUrl, Collections.emptyMap(), null);
    }

    private RegistryTenant createTenant() throws Exception {
        String tenantId = UUID.randomUUID().toString();

        NewRegistryTenantRequest tenantReq = new NewRegistryTenantRequest();
        tenantReq.setOrganizationId("foo");
        tenantReq.setTenantId(tenantId);

        tenantManager.createTenant(tenantReq);
        TestUtils.retry(() -> Assertions.assertNotNull(tenantManager.getTenant(tenantId)));
        return tenantManager.getTenant(tenantId);
    }

    private void updateTenantStatus(RegistryTenant tenant, TenantStatusValue status) throws Exception {
        UpdateRegistryTenantRequest request = new UpdateRegistryTenantRequest();
        request.setStatus(status);
        tenantManager.updateTenant(tenant.getTenantId(), request);
        TestUtils.retry(() -> Assertions.assertEquals(status, tenantManager.getTenant(tenant.getTenantId()).getStatus()));
    }
}
