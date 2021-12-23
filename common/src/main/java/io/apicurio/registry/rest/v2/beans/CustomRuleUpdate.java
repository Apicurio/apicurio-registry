
package io.apicurio.registry.rest.v2.beans;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Root Type for CustomRuleUpdate
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "description",
    "webhookConfig"
})
@Generated("jsonschema2pojo")
@io.quarkus.runtime.annotations.RegisterForReflection
@lombok.Builder
@lombok.AllArgsConstructor
@lombok.NoArgsConstructor
@lombok.EqualsAndHashCode
@lombok.ToString
public class CustomRuleUpdate {

    @JsonProperty("description")
    private String description;
    /**
     * Root Type for WebhookCustomRuleConfig
     * <p>
     * 
     * 
     */
    @JsonProperty("webhookConfig")
    @JsonPropertyDescription("")
    private WebhookCustomRuleConfig webhookConfig;

    @JsonProperty("description")
    public String getDescription() {
        return description;
    }

    @JsonProperty("description")
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Root Type for WebhookCustomRuleConfig
     * <p>
     * 
     * 
     */
    @JsonProperty("webhookConfig")
    public WebhookCustomRuleConfig getWebhookConfig() {
        return webhookConfig;
    }

    /**
     * Root Type for WebhookCustomRuleConfig
     * <p>
     * 
     * 
     */
    @JsonProperty("webhookConfig")
    public void setWebhookConfig(WebhookCustomRuleConfig webhookConfig) {
        this.webhookConfig = webhookConfig;
    }

}
