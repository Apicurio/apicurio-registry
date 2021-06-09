
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
    "resources",
    "name",
    "description"
})
@Generated("jsonschema2pojo")
public class UpdateRegistryTenantRequest {

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

}
