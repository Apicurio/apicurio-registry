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

import io.apicurio.tenantmanager.api.datamodel.TenantStatusValue;
import io.apicurio.tenantmanager.api.datamodel.UpdateApicurioTenantRequest;
import io.apicurio.tenantmanager.client.TenantManagerClient;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.exception.RestClientException;
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.common.ApicurioRegistryBaseIT;
import io.apicurio.tests.common.Constants;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Fabian Martinez
 * @author Jakub Senko <jsenko@redhat.com>
 */
@Tag(Constants.MULTITENANCY)
public class TenantReaperIT extends ApicurioRegistryBaseIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(TenantReaperIT.class);

    private static String groupId = "testGroup";

    @Test
    public void testTenantReaper() throws Exception {
        try {
            MultitenancySupport mt = new MultitenancySupport();

            List<TenantUserClient> tenants = new ArrayList<>(55);

            TenantManagerClient tenantManager = mt.getTenantManagerClient();

            // Create 55 tenants to force use of pagination (currently 50), and some data
            for (int i = 0; i < 55; i++) {
                TenantUserClient tenant = mt.createTenant();
                tenants.add(tenant);
                createSomeArtifact(tenant.client);
                createSomeArtifact(tenant.client);
                Assertions.assertEquals(2, tenant.client.listArtifactsInGroup(groupId).getCount());
            }

            // Mark 53 tenants for deletion
            for (int i = 0; i < 53; i++) {
                updateTenantStatus(tenantManager, tenants.get(i).user.tenantId, TenantStatusValue.TO_BE_DELETED);
            }

            // Wait for the reaper
            TestUtils.waitFor("tenant reaper", 3000, 6 * 3000, () -> {
                for (int i = 0; i < 53; i++) {
                    if (tenantManager.getTenant(tenants.get(i).user.tenantId).getStatus() != TenantStatusValue.DELETED) {
                        return false;
                    }
                }
                return true;
            });

            // Ensure that the APIs are disabled
            for (int i = 0; i < 53; i++) {
                RegistryClient client = tenants.get(i).client;
                try {
                    client.listArtifactsInGroup(groupId);
                    Assertions.fail("This request should not succeed");
                } catch (RestClientException ex) {
                    // OK
                    Assertions.assertEquals(404, ex.getError().getErrorCode());
                }
            }

            // To test that the data was removed, we will change the tenant status back to ready
            for (int i = 0; i < 53; i++) {
                updateTenantStatus(tenantManager, tenants.get(i).user.tenantId, TenantStatusValue.READY);
            }

            // Wait for the reaper again, because it also purges the tenant loader cache
            TestUtils.waitFor("tenant reaper 2", 3000, 6 * 3000, () -> {
                try {
                    for (int i = 0; i < 53; i++) {
                        RegistryClient client = tenants.get(i).client;
                        client.listArtifactsInGroup(groupId);
                    }
                    return true; // The API is available again
                } catch (RestClientException ex) {
                    return false;
                }
            });

            // First 53 tenants should be "empty" and the last 2 should keep their content
            for (int i = 0; i < 53; i++) {
                RegistryClient client = tenants.get(i).client;
                Assertions.assertEquals(0, client.listArtifactsInGroup(groupId).getCount());
            }
            Assertions.assertEquals(2, tenants.get(53).client.listArtifactsInGroup(groupId).getCount());
            Assertions.assertEquals(2, tenants.get(54).client.listArtifactsInGroup(groupId).getCount());
        } catch (RestClientException restClientException) {
            LOGGER.warn("Unexpected rest client exception", restClientException);
            LOGGER.warn("Error code {} message {}", restClientException.getError().getErrorCode(), restClientException.getError().getMessage());
            throw restClientException;
        }

    }

    private void createSomeArtifact(RegistryClient client) throws Exception {
        String artifactId = TestUtils.generateArtifactId();
        ArtifactMetaData meta = client.createArtifact(groupId, artifactId, ArtifactType.JSON, new ByteArrayInputStream("{}".getBytes()));
        TestUtils.retry(() -> client.getContentByGlobalId(meta.getGlobalId()));
        assertNotNull(client.getLatestArtifact(meta.getGroupId(), meta.getId()));
    }

    private void updateTenantStatus(TenantManagerClient tenantManager, String tenantId, TenantStatusValue status) throws Exception {
        UpdateApicurioTenantRequest request = new UpdateApicurioTenantRequest();
        request.setStatus(status);
        tenantManager.updateTenant(tenantId, request);
        TestUtils.retry(() -> Assertions.assertEquals(status, tenantManager.getTenant(tenantId).getStatus()));
    }

}
