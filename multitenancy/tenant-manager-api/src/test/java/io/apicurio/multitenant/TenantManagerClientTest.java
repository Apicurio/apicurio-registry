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
package io.apicurio.multitenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.apicurio.multitenant.client.TenantManagerClient;
import io.apicurio.multitenant.client.TenantManagerClientImpl;
import io.apicurio.multitenant.client.exception.RegistryTenantNotFoundException;
import io.apicurio.multitenant.api.datamodel.NewRegistryTenantRequest;
import io.apicurio.multitenant.api.datamodel.RegistryTenant;
import io.apicurio.multitenant.api.datamodel.ResourceType;
import io.apicurio.multitenant.api.datamodel.TenantResource;
import io.apicurio.multitenant.api.datamodel.UpdateRegistryTenantRequest;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.TestInstance;

/**
 * @author Fabian Martinez
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TenantManagerClientTest {

    private static TenantManagerClient client;

    @BeforeAll
    public void beforeAll() {
        client = createRestClient();
    }

    protected TenantManagerClient createRestClient() {
        return new TenantManagerClientImpl("http://localhost:8081/");
    }

    @BeforeEach
    public void cleanup() {
        List<RegistryTenant> list = client.listTenants();
        list.forEach(t -> client.deleteTenant(t.getTenantId()));
        list = client.listTenants();
        assertEquals(0, list.size());
    }

    @Test
    public void testCRUD() {
        NewRegistryTenantRequest req = new NewRegistryTenantRequest();
        req.setTenantId(UUID.randomUUID().toString());
        req.setOrganizationId("aaa");
        TenantResource tr = new TenantResource();
        tr.setLimit(5L);
        tr.setType(ResourceType.MAX_TOTAL_SCHEMAS_COUNT);
        req.setResources(List.of(tr));

        RegistryTenant tenant = client.createTenant(req);

        assertNotNull(tenant);
        assertNotNull(tenant.getTenantId());
        assertNotNull(tenant.getCreatedOn());
        assertNotNull(tenant.getResources());
        assertFalse(tenant.getResources().isEmpty());

        testGetTenant(tenant.getTenantId(), req);

        testUpdateTenant(tenant.getTenantId());

        testDelete(tenant.getTenantId());
    }

    @Test
    public void testNotFound() {
        testTenantNotFound("abcede");
    }

    private void testGetTenant(String tenantId, NewRegistryTenantRequest req) {
        RegistryTenant tenant = client.getTenant(tenantId);

        assertEquals(tenantId, tenant.getTenantId());
        assertEquals(req.getOrganizationId(), tenant.getOrganizationId());
        assertNotNull(req.getResources());
        assertNotNull(tenant.getResources());
        assertEquals(RegistryTenantResourceTest.toString(req.getResources()), RegistryTenantResourceTest.toString(tenant.getResources()));
    }

    private void testUpdateTenant(String tenantId) {
        UpdateRegistryTenantRequest req = new UpdateRegistryTenantRequest();
        req.setDescription("new description");
        req.setName("new name");
        TenantResource tr = new TenantResource();
        tr.setLimit(256L);
        tr.setType(ResourceType.MAX_LABEL_SIZE_BYTES);
        req.setResources(List.of(tr));

        client.updateTenant(tenantId, req);

        testGetTenantUpdated(tenantId, req);
    }

    private void testGetTenantUpdated(String tenantId, UpdateRegistryTenantRequest req) {
        RegistryTenant tenant = client.getTenant(tenantId);

        assertEquals(tenantId, tenant.getTenantId());
        assertNotNull(req.getResources());
        assertNotNull(tenant.getResources());
        assertEquals(req.getName(), tenant.getName());
        assertEquals(req.getDescription(), tenant.getDescription());
        assertEquals(RegistryTenantResourceTest.toString(req.getResources()), RegistryTenantResourceTest.toString(tenant.getResources()));
    }

    public void testDelete(String tenantId) {
        client.deleteTenant(tenantId);
        testTenantNotFound(tenantId);
    }

    private void testTenantNotFound(String tenantId) {
        Assertions.assertThrows(RegistryTenantNotFoundException.class, () -> {
            client.getTenant(tenantId);
        });
    }
}
