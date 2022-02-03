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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.apicurio.multitenant.client.TenantManagerClient;
import io.apicurio.multitenant.client.TenantManagerClientImpl;
import io.apicurio.multitenant.client.exception.RegistryTenantNotFoundException;
import io.apicurio.multitenant.client.exception.TenantManagerClientException;
import io.apicurio.multitenant.logging.audit.MockAuditLogService;
import io.apicurio.multitenant.api.datamodel.NewRegistryTenantRequest;
import io.apicurio.multitenant.api.datamodel.RegistryTenant;
import io.apicurio.multitenant.api.datamodel.ResourceType;
import io.apicurio.multitenant.api.datamodel.SortBy;
import io.apicurio.multitenant.api.datamodel.SortOrder;
import io.apicurio.multitenant.api.datamodel.TenantResource;
import io.apicurio.multitenant.api.datamodel.TenantStatusValue;
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

    @Inject
    MockAuditLogService auditLogService;

    @BeforeAll
    public void beforeAll() {
        client = createRestClient();
    }

    protected TenantManagerClient createRestClient() {
        return new TenantManagerClientImpl("http://localhost:8081/");
    }

    @BeforeEach
    @SuppressWarnings("deprecation")
    public void cleanup() {
        List<RegistryTenant> list = client.listTenants();
        list.forEach(t -> {
            if (t.getStatus() == TenantStatusValue.READY) {
                client.deleteTenant(t.getTenantId());
            }
        });
        var search = client.listTenants(TenantStatusValue.READY, null, null, null, null);
        list = search.getItems();
        assertEquals(0, list.size());
        assertEquals(0, search.getCount());
    }

    @Test
    public void testCRUD() {
        auditLogService.resetAuditLogs();

        //CRUD tenant1
        NewRegistryTenantRequest req = new NewRegistryTenantRequest();
        req.setTenantId(UUID.randomUUID().toString());
        req.setOrganizationId("aaa");
        req.setName("tenant1");
        req.setCreatedBy("apicurio");
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
        assertEquals(req.getTenantId(), tenant.getTenantId());
        assertEquals(req.getOrganizationId(), tenant.getOrganizationId());
        assertEquals(req.getName(), tenant.getName());
        assertEquals(req.getCreatedBy(), tenant.getCreatedBy());

        testGetTenant(tenant.getTenantId(), req);

        testUpdateTenant(tenant.getTenantId());

        RegistryTenant tenant1updated = client.getTenant(tenant.getTenantId());

        testDelete(tenant.getTenantId());

        //create tenant2
        NewRegistryTenantRequest req2 = new NewRegistryTenantRequest();
        req2.setTenantId(UUID.randomUUID().toString());
        req2.setOrganizationId("bbb");
        req2.setName("tenant2");
        req2.setCreatedBy("registry");

        RegistryTenant tenant2 = client.createTenant(req2);

        assertNotNull(tenant2);
        assertNotNull(tenant2.getTenantId());
        assertNotNull(tenant2.getCreatedOn());
        assertNotNull(tenant2.getResources());
        assertTrue(tenant2.getResources().isEmpty());
        assertEquals(req2.getTenantId(), tenant2.getTenantId());
        assertEquals(req2.getOrganizationId(), tenant2.getOrganizationId());

        //verify audit logs
        List<Map<String, String>> auditLogs = auditLogService.getAuditLogs();
        assertEquals(4, auditLogs.size());

        //createTenant entry
        var audit = auditLogs.get(0);
        assertEquals(tenant.getTenantId(), audit.get("tenantId"));
        assertEquals("createTenant", audit.get("action"));
        assertEquals("success", audit.get("result"));
        assertEquals(tenant.getOrganizationId(), audit.get("orgId"));
        assertEquals(tenant.getName(), audit.get("name"));
        assertEquals(tenant.getCreatedBy(), audit.get("createdBy"));

        //updateTenant entry
        audit = auditLogs.get(1);
        assertEquals(tenant1updated.getTenantId(), audit.get("tenantId"));
        assertEquals("updateTenant", audit.get("action"));
        assertEquals("success", audit.get("result"));
        assertEquals(tenant1updated.getStatus().value(), audit.get("tenantStatus"));

        //deleteTenant entry
        audit = auditLogs.get(2);
        assertEquals(tenant.getTenantId(), audit.get("tenantId"));
        assertEquals("deleteTenant", audit.get("action"));
        assertEquals("success", audit.get("result"));

        //createTenant tenant2 entry
        audit = auditLogs.get(3);
        assertEquals(tenant2.getTenantId(), audit.get("tenantId"));
        assertEquals("createTenant", audit.get("action"));
        assertEquals("success", audit.get("result"));
        assertEquals(tenant2.getOrganizationId(), audit.get("orgId"));
        assertEquals(tenant2.getName(), audit.get("name"));
        assertEquals(tenant2.getCreatedBy(), audit.get("createdBy"));
    }

    @Test
    public void testPagination() {
        int totalItems = 15;
        for (int i = 0; i<totalItems ; i++ ) {
            NewRegistryTenantRequest req = new NewRegistryTenantRequest();
            req.setTenantId(UUID.randomUUID().toString());
            req.setOrganizationId(UUID.randomUUID().toString());
            client.createTenant(req);
        }

        var search = client.listTenants(TenantStatusValue.READY, 0, 5, SortOrder.asc, SortBy.tenantId);
        assertEquals(5, search.getItems().size());
        assertEquals(totalItems, search.getCount());

        search = client.listTenants(TenantStatusValue.READY, 5, 5, SortOrder.asc, SortBy.tenantId);
        assertEquals(5, search.getItems().size());
        assertEquals(totalItems, search.getCount());

        search = client.listTenants(TenantStatusValue.READY, 10, 5, SortOrder.asc, SortBy.tenantId);
        assertEquals(5, search.getItems().size());
        assertEquals(totalItems, search.getCount());

        search = client.listTenants(TenantStatusValue.READY, 15, 5, SortOrder.asc, SortBy.tenantId);
        assertEquals(0, search.getItems().size());
        assertEquals(totalItems, search.getCount());
    }

    @Test
    public void testApiValidation() {
        Assertions.assertThrows(TenantManagerClientException.class, () -> client.listTenants(TenantStatusValue.READY, -1, 5000000, SortOrder.asc, SortBy.name));
        Assertions.assertThrows(TenantManagerClientException.class, () -> client.listTenants(TenantStatusValue.READY, 0, -5, SortOrder.asc, SortBy.name));
        Assertions.assertThrows(TenantManagerClientException.class, () -> client.listTenants(TenantStatusValue.READY, 0, 585685, SortOrder.asc, SortBy.name));
        client.listTenants(null, null, null, null, null);
    }

    @Test
    public void testNotFound() {
        testTenantNotFound("abcede");
    }

    @Test
    public void testCreateDeleteCreate() {
        NewRegistryTenantRequest req = new NewRegistryTenantRequest();
        String tenantId = "testCreateDeleteCreate";
        req.setTenantId(tenantId);
        req.setOrganizationId("aaa");

        client.createTenant(req);
        assertEquals(TenantStatusValue.READY, client.getTenant(tenantId).getStatus());

        client.deleteTenant(tenantId);
        assertEquals(TenantStatusValue.TO_BE_DELETED, client.getTenant(tenantId).getStatus());

        //transition not allowed
        UpdateRegistryTenantRequest upready = new UpdateRegistryTenantRequest();
        upready.setStatus(TenantStatusValue.READY);
        Assertions.assertThrows(TenantManagerClientException.class, () -> client.updateTenant(tenantId, upready));

        UpdateRegistryTenantRequest updr = new UpdateRegistryTenantRequest();
        updr.setStatus(TenantStatusValue.DELETED);
        client.updateTenant(tenantId, updr);
        assertEquals(TenantStatusValue.DELETED, client.getTenant(tenantId).getStatus());

        //tenant already exists
        Assertions.assertThrows(TenantManagerClientException.class, () -> client.createTenant(req));
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
        req.setStatus(TenantStatusValue.READY);

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
        RegistryTenant tenant = client.getTenant(tenantId);
        assertEquals(TenantStatusValue.TO_BE_DELETED, tenant.getStatus());
    }

    private void testTenantNotFound(String tenantId) {
        Assertions.assertThrows(RegistryTenantNotFoundException.class, () -> {
            client.getTenant(tenantId);
        });
    }
}
