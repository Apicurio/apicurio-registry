
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
    "createdBy",
    "authServerUrl",
    "clientId"

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
     * User who requested the tenant.
     *
     */
    @JsonProperty("createdBy")
    @JsonPropertyDescription("User who created the tenant")
    private String createdBy;

    /**
     * Http endpoint for the auth server (including realm) to be used for this tenant to authenticate against the registry
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
    @JsonProperty("clientId")
    @JsonPropertyDescription("ClientId in the authentication server to be used by the registry to authenticate incoming requests made by the tenant")
    private String clientId;

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
     * Http endpoint for the auth server (including realm) to be used for this tenant to authenticate against the registry
     *
     */
    @JsonProperty("authServerUrl")
    public String getAuthServerUrl() {
        return authServerUrl;
    }

    /**
     * Http endpoint for the auth server (including realm) to be used for this tenant to authenticate against the registry
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
    @JsonProperty("clientId")
    public String getClientId() {
        return clientId;
    }

    /**
     * ClientId in the authentication server to be used by the registry to authenticate incoming requests made by the tenant
     * (Required)
     *
     */
    @JsonProperty("clientId")
    public void setClientId(String clientId) {
        this.clientId = clientId;
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
     * User who requested the tenant.
     *
     */
    @JsonProperty("createdBy")
    public String getCreatedBy() {
        return createdBy;
    }

    /**
     * User who requested the tenant.
     *
     */
    @JsonProperty("createdBy")
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
}