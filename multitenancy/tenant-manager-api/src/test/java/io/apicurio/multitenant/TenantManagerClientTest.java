package io.apicurio.multitenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.apicurio.multitenant.client.TenantManagerClient;
import io.apicurio.multitenant.client.TenantManagerClientImpl;
import io.apicurio.multitenant.client.exception.TenantManagerClientException;
import io.apicurio.multitenant.datamodel.NewRegistryTenantRequest;
import io.apicurio.multitenant.datamodel.RegistryTenant;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class TenantManagerClientTest {

    private TenantManagerClient client = new TenantManagerClientImpl("http://localhost:8081/");

    @Test
    public void testListTenants() {
        List<RegistryTenant> list = client.listTenants();
        assertEquals(0, list.size());
    }

    @Test
    public void testCRUD() {
        NewRegistryTenantRequest req = new NewRegistryTenantRequest();
        req.setOrganizationId("aaa");
        req.setDeploymentFlavor("small");
        req.setClientId("aaaaa");

        RegistryTenant tenant = client.createTenant(req);

        assertNotNull(tenant);
        assertNotNull(tenant.getTenantId());
        assertNotNull(tenant.getCreatedOn());

        testGetTenant(tenant.getTenantId(), req);

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
    }

    public void testDelete(String tenantId) {
        client.deleteTenant(tenantId);
        testTenantNotFound(tenantId);
    }

    private void testTenantNotFound(String tenantId) {
        Assertions.assertThrows(TenantManagerClientException.class, () -> {
            client.getTenant(tenantId);
        });
    }
}
