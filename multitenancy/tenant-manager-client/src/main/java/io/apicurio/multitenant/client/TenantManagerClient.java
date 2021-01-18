package io.apicurio.multitenant.client;

import java.util.List;
import io.apicurio.multitenant.datamodel.NewRegistryTenantRequest;
import io.apicurio.multitenant.datamodel.RegistryTenant;

public interface TenantManagerClient {

    public List<RegistryTenant> listTenants();

    public RegistryTenant createTenant(NewRegistryTenantRequest tenantRequest);

    public RegistryTenant getTenant(String tenantId);

    public void deleteTenant(String tenantId);

}
