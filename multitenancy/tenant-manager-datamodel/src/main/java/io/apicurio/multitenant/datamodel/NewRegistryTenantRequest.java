package io.apicurio.multitenant.datamodel;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
@JsonInclude(Include.NON_NULL)
public class NewRegistryTenantRequest {

    private String organizationId;
    private String deploymentFlavor;

    public NewRegistryTenantRequest() {
        //empty
    }

    public String getOrganizationId() {
        return organizationId;
    }
    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }
    public String getDeploymentFlavor() {
        return deploymentFlavor;
    }
    public void setDeploymentFlavor(String deploymentFlavor) {
        this.deploymentFlavor = deploymentFlavor;
    }

}
