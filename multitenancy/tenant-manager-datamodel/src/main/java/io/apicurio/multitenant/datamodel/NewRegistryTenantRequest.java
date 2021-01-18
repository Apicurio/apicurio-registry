package io.apicurio.multitenant.datamodel;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
@JsonInclude(Include.NON_NULL)
public class NewRegistryTenantRequest {

    private String deploymentFlavor;

    private String organizationId;
    private String clientId;
    private String authServerUrl;

    public NewRegistryTenantRequest() {
        // empty
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getAuthServerUrl() {
        return authServerUrl;
    }

    public void setAuthServerUrl(String authServerUrl) {
        this.authServerUrl = authServerUrl;
    }

    public String getDeploymentFlavor() {
        return deploymentFlavor;
    }

    public void setDeploymentFlavor(String deploymentFlavor) {
        this.deploymentFlavor = deploymentFlavor;
    }

}
