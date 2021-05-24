
package io.apicurio.multitenant.api.datamodel;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Root Type for Tenant
 * <p>
 * Models a single tenant.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "tenantId",
    "createdOn",
    "createdBy",
    "organizationId"
})
public class RegistryTenant {

    /**
     * Unique identifier of a tenant within a registry deployment
     * (Required)
     * 
     */
    @JsonProperty("tenantId")
    @JsonPropertyDescription("Unique identifier of a tenant within a registry deployment")
    private String tenantId;
    /**
     * Date when the tenant was created
     * (Required)
     * 
     */
    @JsonProperty("createdOn")
    @JsonPropertyDescription("Date when the tenant was created")
    private Date createdOn;
    /**
     * User that created the tenant
     * (Required)
     * 
     */
    @JsonProperty("createdBy")
    @JsonPropertyDescription("User that created the tenant")
    private String createdBy;
    /**
     * 
     */
    @JsonProperty("organizationId")
    @JsonPropertyDescription("")
    private Object organizationId;

    //TODO properly generate this class
    private RegistryTenantLimits limits;

    /**
     * Unique identifier of a tenant within a registry deployment
     * (Required)
     *
     */
    @JsonProperty("tenantId")
    public String getTenantId() {
        return tenantId;
    }

    /**
     * Unique identifier of a tenant within a registry deployment
     * (Required)
     * 
     */
    @JsonProperty("tenantId")
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    /**
     * Date when the tenant was created
     * (Required)
     * 
     */
    @JsonProperty("createdOn")
    public Date getCreatedOn() {
        return createdOn;
    }

    /**
     * Date when the tenant was created
     * (Required)
     * 
     */
    @JsonProperty("createdOn")
    public void setCreatedOn(Date createdOn) {
        this.createdOn = createdOn;
    }

    /**
     * User that created the tenant
     * (Required)
     * 
     */
    @JsonProperty("createdBy")
    public String getCreatedBy() {
        return createdBy;
    }

    /**
     * User that created the tenant
     * (Required)
     * 
     */
    @JsonProperty("createdBy")
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    /**
     * 
     */
    @JsonProperty("organizationId")
    public Object getOrganizationId() {
        return organizationId;
    }

    /**
     * 
     */
    @JsonProperty("organizationId")
    public void setOrganizationId(Object organizationId) {
        this.organizationId = organizationId;
    }

    /**
     * @return the limits
     */
    public RegistryTenantLimits getLimits() {
        return limits;
    }

    /**
     * @param limits the limits to set
     */
    public void setLimits(RegistryTenantLimits limits) {
        this.limits = limits;
    }

}
