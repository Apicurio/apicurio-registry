
package io.apicurio.registry.rest.v2.beans;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * The mapping between a user/principal and their role.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "principalId",
    "role"
})
@Generated("jsonschema2pojo")
@io.quarkus.runtime.annotations.RegisterForReflection
public class RoleMapping {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("principalId")
    @JsonPropertyDescription("")
    private String principalId;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("role")
    @JsonPropertyDescription("")
    private Role role;

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("principalId")
    public String getPrincipalId() {
        return principalId;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("principalId")
    public void setPrincipalId(String principalId) {
        this.principalId = principalId;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("role")
    public Role getRole() {
        return role;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("role")
    public void setRole(Role role) {
        this.role = role;
    }

}
