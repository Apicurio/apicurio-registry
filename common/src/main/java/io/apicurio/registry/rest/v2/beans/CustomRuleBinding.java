
package io.apicurio.registry.rest.v2.beans;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Root Type for CustomRuleBinding
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "customRuleId"
})
@Generated("jsonschema2pojo")
@io.quarkus.runtime.annotations.RegisterForReflection
@lombok.Builder
@lombok.AllArgsConstructor
@lombok.NoArgsConstructor
@lombok.EqualsAndHashCode
@lombok.ToString
public class CustomRuleBinding {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("customRuleId")
    private String customRuleId;

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("customRuleId")
    public String getCustomRuleId() {
        return customRuleId;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("customRuleId")
    public void setCustomRuleId(String customRuleId) {
        this.customRuleId = customRuleId;
    }

}
