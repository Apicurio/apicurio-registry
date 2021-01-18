package io.apicurio.multitenant.persistence.dto;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;

import io.apicurio.multitenant.datamodel.RegistryTenant;

@Entity
@Table(name = "tenants")
public class RegistryTenantDto {

    @Id
    @GeneratedValue
    private Long id;

    @Column(name = "tenantId")
    private String tenantId;

    private Date createdOn;
    private String createdBy;

    @Column(name = "organizationId")
    private String organizationId;

    private String deploymentFlavor;
    private String status; //TODO extend

    @Column(name = "authServerUrl")
    private String authServerUrl;

    @Column(name = "authClientId")
    private String authClientId;


    public RegistryTenantDto() {
        // empty
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public Date getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(Date createdOn) {
        this.createdOn = createdOn;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }


    public String getAuthServerUrl() {
        return authServerUrl;
    }

    public void setAuthServerUrl(String authServerUrl) {
        this.authServerUrl = authServerUrl;
    }

    public String getAuthClientId() {
        return authClientId;
    }

    public void setAuthClientId(String authClientId) {
        this.authClientId = authClientId;
    }

    public RegistryTenant toDatamodel() {
        final RegistryTenant t = new RegistryTenant();
        t.setTenantId(this.tenantId);
        t.setCreatedOn(this.createdOn);
        t.setCreatedBy(this.createdBy);
        t.setOrganizationId(this.organizationId);
        t.setDeploymentFlavor(this.deploymentFlavor);
        t.setStatus(this.status);
        t.setAuthClientId(this.authClientId);
        t.setAuthServerUrl(this.authServerUrl);
        return t;
    }

}
