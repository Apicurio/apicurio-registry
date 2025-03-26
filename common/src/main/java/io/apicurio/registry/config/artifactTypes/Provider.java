
package io.apicurio.registry.config.artifactTypes;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;


@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "type"
})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = ScriptProvider.class, name = "script"),
        @JsonSubTypes.Type(value = JavaClassProvider.class, name = "java"),
        @JsonSubTypes.Type(value = WebhookProvider.class, name = "webhook")
})
@io.quarkus.runtime.annotations.RegisterForReflection
public abstract class Provider {

    /**
     * (Required)
     */
    @JsonProperty("type")
    private String type;

    /**
     * (Required)
     */
    @JsonProperty("type")
    public String getType() {
        return type;
    }

    /**
     * (Required)
     */
    @JsonProperty("type")
    public void setType(String type) {
        this.type = type;
    }

}
