package io.apicurio.registry.operator.api.v1.status;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import lombok.Getter;
import lombok.Setter;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "host" })
@JsonDeserialize(using = JsonDeserializer.None.class)
@Getter
@Setter
public class Info implements KubernetesResource {

    /**
     * Apicurio Registry URL
     */
    @JsonProperty("host")
    @JsonPropertyDescription("Apicurio Registry URL")
    @JsonSetter(nulls = com.fasterxml.jackson.annotation.Nulls.SKIP)
    private String host;

}
