package io.apicurio.managed.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class RegistryTenant {
    
    private String tenantId;
    private String orgId;

    private String endpoint;

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

}
