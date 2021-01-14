package io.apicurio.multitenant.persistence.dto;

import java.util.Date;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

import io.apicurio.multitenant.datamodel.RegistryTenant;

@Entity
public class RegistryTenantDto {

    @Id
    @GeneratedValue
    private Long id;

    private String tenantId;
    private Date createdOn;
    private String createdBy;
    private String organizationId;
    private String deploymentFlavor;
    private String status; //TODO extend

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

    public RegistryTenant toDatamodel() {
        RegistryTenant t = new RegistryTenant();
        t.setTenantId(this.tenantId);
        t.setCreatedOn(this.createdOn);
        t.setCreatedBy(this.createdBy);
        t.setOrganizationId(this.organizationId);
        t.setDeploymentFlavor(this.deploymentFlavor);
        t.setStatus(this.status);
        return t;
    }

}
