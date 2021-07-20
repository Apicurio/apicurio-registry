
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
    "resources",
    "name",
    "description",
    "createdBy"
})
@Generated("jsonschema2pojo")
public class NewRegistryTenantRequest {

    /**
     * Unique identifier of a tenant within a registry deployment
     * (Required)
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
     * The optional name of the tenant.
     * 
     */
    @JsonProperty("name")
    @JsonPropertyDescription("The optional name of the tenant.")
    private String name;
    /**
     * An optional description for the tenant.
     * 
     */
    @JsonProperty("description")
    @JsonPropertyDescription("An optional description for the tenant.")
    private String description;
    /**
     * User who created the tenant
     * 
     */
    @JsonProperty("createdBy")
    @JsonPropertyDescription("User who created the tenant")
    private String createdBy;

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

    /**
     * The optional name of the tenant.
     * 
     */
    @JsonProperty("name")
    public String getName() {
        return name;
    }

    /**
     * The optional name of the tenant.
     * 
     */
    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

    /**
     * An optional description for the tenant.
     * 
     */
    @JsonProperty("description")
    public String getDescription() {
        return description;
    }

    /**
     * An optional description for the tenant.
     * 
     */
    @JsonProperty("description")
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * User who created the tenant
     * 
     */
    @JsonProperty("createdBy")
    public String getCreatedBy() {
        return createdBy;
    }

    /**
     * User who created the tenant
     * 
     */
    @JsonProperty("createdBy")
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

}
