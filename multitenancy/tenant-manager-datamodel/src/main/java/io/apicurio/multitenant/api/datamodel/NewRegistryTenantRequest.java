
package io.apicurio.multitenant.api.datamodel;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Root Type for NewTenant
 * <p>
 * The information required when creating a new tenant.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "tenantId",
    "organizationId",
    "resources"
})
@Generated("jsonschema2pojo")
public class NewRegistryTenantRequest {

    /**
     * Unique identifier of a tenant within a registry deployment
     * 
     */
    @JsonProperty("tenantId")
    @JsonPropertyDescription("Unique identifier of a tenant within a registry deployment")
    private String tenantId;
    /**
     * ID of the organization the tenant belongs to
     * (Required)
     * 
     */
    @JsonProperty("organizationId")
    @JsonPropertyDescription("ID of the organization the tenant belongs to")
    private String organizationId;
    /**
     * The list of resources that this tenant will have at max. available
     * 
     */
    @JsonProperty("resources")
    @JsonPropertyDescription("The list of resources that this tenant will have at max. available")
    private List<TenantResource> resources = new ArrayList<TenantResource>();

    /**
     * Unique identifier of a tenant within a registry deployment
     * 
     */
    @JsonProperty("tenantId")
    public String getTenantId() {
        return tenantId;
    }

    /**
     * Unique identifier of a tenant within a registry deployment
     * 
     */
    @JsonProperty("tenantId")
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    /**
     * ID of the organization the tenant belongs to
     * (Required)
     * 
     */
    @JsonProperty("organizationId")
    public String getOrganizationId() {
        return organizationId;
    }

    /**
     * ID of the organization the tenant belongs to
     * (Required)
     * 
     */
    @JsonProperty("organizationId")
    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    /**
     * The list of resources that this tenant will have at max. available
     * 
     */
    @JsonProperty("resources")
    public List<TenantResource> getResources() {
        return resources;
    }

    /**
     * The list of resources that this tenant will have at max. available
     * 
     */
    @JsonProperty("resources")
    public void setResources(List<TenantResource> resources) {
        this.resources = resources;
    }

}
