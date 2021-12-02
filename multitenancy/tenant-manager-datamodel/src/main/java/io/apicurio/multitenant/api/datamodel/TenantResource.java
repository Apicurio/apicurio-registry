
package io.apicurio.multitenant.api.datamodel;

import javax.annotation.processing.Generated;
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
@Generated("jsonschema2pojo")
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

}
