
package io.apicurio.multitenant.api.datamodel;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Root Type for TenantResource
 * <p>
 * Configuration of the limits for a specific resource type
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "type",
    "limit"
})
public class TenantResource {

    /**
     * The list of possible types of a resource that can be limited
     * (Required)
     * 
     */
    @JsonProperty("type")
    @JsonPropertyDescription("The list of possible types of a resource that can be limited")
    private ResourceType type;
    /**
     * The quantity to limit this resource
     * (Required)
     * 
     */
    @JsonProperty("limit")
    @JsonPropertyDescription("The quantity to limit this resource")
    private Long limit;

    /**
     * The list of possible types of a resource that can be limited
     * (Required)
     * 
     */
    @JsonProperty("type")
    public ResourceType getType() {
        return type;
    }

    /**
     * The list of possible types of a resource that can be limited
     * (Required)
     * 
     */
    @JsonProperty("type")
    public void setType(ResourceType type) {
        this.type = type;
    }

    /**
     * The quantity to limit this resource
     * (Required)
     * 
     */
    @JsonProperty("limit")
    public Long getLimit() {
        return limit;
    }

    /**
     * The quantity to limit this resource
     * (Required)
     *
     */
    @JsonProperty("limit")
    public void setLimit(Long limit) {
        this.limit = limit;
    }

    /**
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((limit == null) ? 0 : limit.hashCode());
        result = prime * result + ((type == null) ? 0 : type.hashCode());
        return result;
    }

    /**
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        TenantResource other = (TenantResource) obj;
        if (limit == null) {
            if (other.limit != null)
                return false;
        } else if (!limit.equals(other.limit))
            return false;
        if (type != other.type)
            return false;
        return true;
    }

}
