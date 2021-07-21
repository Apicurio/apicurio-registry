
package io.apicurio.multitenant.api.datamodel;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonFormat;
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
    "organizationId",
    "resources",
    "name",
    "description",
    "authServerUrl",
    "authClientId"
})
@Generated("jsonschema2pojo")
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
     * Date when the tenant was created. ISO 8601 UTC timestamp.
     * (Required)
     * 
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssZ", timezone = "UTC")
    @JsonProperty("createdOn")
    @JsonPropertyDescription("Date when the tenant was created. ISO 8601 UTC timestamp.")
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
     * (Required)
     * 
     */
    @JsonProperty("organizationId")
    @JsonPropertyDescription("")
    private Object organizationId;
    /**
     * Http endpoint for the auth server (including realm) to be used for this tenant to authenticate against the registry
     * (Required)
     *
     */
    @JsonProperty("authServerUrl")
    @JsonPropertyDescription("Http endpoint for the auth server (including realm) to be used for this tenant to authenticate against the registry")
    private String authServerUrl;
    /**
     * ClientId in the authentication server to be used by the registry to authenticate incoming requests made by the tenant
     * (Required)
     *
     */
    @JsonProperty("authClientId")
    @JsonPropertyDescription("ClientId in the authentication server to be used by the registry to authenticate incoming requests made by the tenant")
    private String authClientId;

    /**
     * The list of resources that this tenant has available
     * 
     */
    @JsonProperty("resources")
    @JsonPropertyDescription("The list of resources that this tenant has available")
    private List<TenantResource> resources = new ArrayList<TenantResource>();
    /**
     * The name of the tenant.
     * 
     */
    @JsonProperty("name")
    @JsonPropertyDescription("The name of the tenant.")
    private String name;
    /**
     * The description of the tenant.
     * 
     */
    @JsonProperty("description")
    @JsonPropertyDescription("The description of the tenant.")
    private String description;

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
     * Date when the tenant was created. ISO 8601 UTC timestamp.
     * (Required)
     * 
     */
    @JsonProperty("createdOn")
    public Date getCreatedOn() {
        return createdOn;
    }

    /**
     * Date when the tenant was created. ISO 8601 UTC timestamp.
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
     * (Required)
     * 
     */
    @JsonProperty("organizationId")
    public Object getOrganizationId() {
        return organizationId;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("organizationId")
    public void setOrganizationId(Object organizationId) {
        this.organizationId = organizationId;
    }

    /**
     * Http endpoint for the auth server (including realm) to be used for this tenant to authenticate against the registry
     * (Required)
     *
     */
    @JsonProperty("authServerUrl")
    public String getAuthServerUrl() {
        return authServerUrl;
    }

    /**
     * Http endpoint for the auth server (including realm) to be used for this tenant to authenticate against the registry
     * (Required)
     *
     */
    @JsonProperty("authServerUrl")
    public void setAuthServerUrl(String authServerUrl) {
        this.authServerUrl = authServerUrl;
    }

    /**
     * ClientId in the authentication server to be used by the registry to authenticate incoming requests made by the tenant
     * (Required)
     *
     */
    @JsonProperty("authClientId")
    public String getAuthClientId() {
        return authClientId;
    }

    /**
     * ClientId in the authentication server to be used by the registry to authenticate incoming requests made by the tenant
     * (Required)
     *
     */
    @JsonProperty("authClientId")
    public void setAuthClientId(String authClientId) {
        this.authClientId = authClientId;
    }

    /**
     * The list of resources that this tenant has available
     * 
     */
    @JsonProperty("resources")
    public List<TenantResource> getResources() {
        return resources;
    }

    /**
     * The list of resources that this tenant has available
     * 
     */
    @JsonProperty("resources")
    public void setResources(List<TenantResource> resources) {
        this.resources = resources;
    }

    /**
     * The name of the tenant.
     * 
     */
    @JsonProperty("name")
    public String getName() {
        return name;
    }

    /**
     * The name of the tenant.
     * 
     */
    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

    /**
     * The description of the tenant.
     * 
     */
    @JsonProperty("description")
    public String getDescription() {
        return description;
    }

    /**
     * The description of the tenant.
     * 
     */
    @JsonProperty("description")
    public void setDescription(String description) {
        this.description = description;
    }

}
