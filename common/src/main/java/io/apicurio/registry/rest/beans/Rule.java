
package io.apicurio.registry.rest.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.apicurio.registry.types.RuleType;


/**
 * Root Type for Rule
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "config",
    "type"
})
public class Rule {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("config")
    private String config;
    /**
     * 
     */
    @JsonProperty("type")
    @JsonPropertyDescription("")
    private RuleType type;

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("config")
    public String getConfig() {
        return config;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("config")
    public void setConfig(String config) {
        this.config = config;
    }

    /**
     * 
     */
    @JsonProperty("type")
    public RuleType getType() {
        return type;
    }

    /**
     * 
     */
    @JsonProperty("type")
    public void setType(RuleType type) {
        this.type = type;
    }

}
