package io.apicurio.registry.operator.api.v1;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.JsonDeserializer.None;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import lombok.*;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({ "appHost", "uiHost" })
@JsonDeserialize(using = None.class)
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Getter
@Setter
@ToString
public class ApicurioRegistry3Spec implements KubernetesResource {

    /**
     * Apicurio Registry backend base URL
     */
    @JsonProperty("appHost")
    @JsonPropertyDescription("Apicurio Registry backend base URL")
    @JsonSetter(nulls = Nulls.SKIP)
    private String appHost;

    /**
     * Apicurio Registry UI URL
     */
    @JsonProperty("uiHost")
    @JsonPropertyDescription("Apicurio Registry UI base URL")
    @JsonSetter(nulls = Nulls.SKIP)
    private String uiHost;
}
