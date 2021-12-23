
package io.apicurio.registry.rest.v2.beans;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.CustomRuleType;


/**
 * Root Type for CustomRuleInfo
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "id",
    "description",
    "customRuleType",
    "supportedArtifactType"
})
@Generated("jsonschema2pojo")
@io.quarkus.runtime.annotations.RegisterForReflection
@lombok.Builder
@lombok.AllArgsConstructor
@lombok.NoArgsConstructor
@lombok.EqualsAndHashCode
@lombok.ToString
public class CustomRuleInfo {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("id")
    private String id;
    @JsonProperty("description")
    private String description;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("customRuleType")
    @JsonPropertyDescription("")
    private CustomRuleType customRuleType;
    /**
     * 
     */
    @JsonProperty("supportedArtifactType")
    @JsonPropertyDescription("")
    private ArtifactType supportedArtifactType;

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("id")
    public String getId() {
        return id;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("id")
    public void setId(String id) {
        this.id = id;
    }

    @JsonProperty("description")
    public String getDescription() {
        return description;
    }

    @JsonProperty("description")
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("customRuleType")
    public CustomRuleType getCustomRuleType() {
        return customRuleType;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("customRuleType")
    public void setCustomRuleType(CustomRuleType customRuleType) {
        this.customRuleType = customRuleType;
    }

    /**
     * 
     */
    @JsonProperty("supportedArtifactType")
    public ArtifactType getSupportedArtifactType() {
        return supportedArtifactType;
    }

    /**
     * 
     */
    @JsonProperty("supportedArtifactType")
    public void setSupportedArtifactType(ArtifactType supportedArtifactType) {
        this.supportedArtifactType = supportedArtifactType;
    }

}
