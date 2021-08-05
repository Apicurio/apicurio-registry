
package io.apicurio.registry.rest.v2.beans;

import javax.annotation.processing.Generated;
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
@Generated("jsonschema2pojo")
@io.quarkus.runtime.annotations.RegisterForReflection
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

    //TODO generate hashCode and equals from apicurio studio

    /**
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((config == null) ? 0 : config.hashCode());
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
        Rule other = (Rule) obj;
        if (config == null) {
            if (other.config != null)
                return false;
        } else if (!config.equals(other.config))
            return false;
        if (type != other.type)
            return false;
        return true;
    }

}
