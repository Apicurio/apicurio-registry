package io.apicurio.registry.operator.api.v1.status;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.JsonDeserializer.None;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import lombok.*;
import lombok.experimental.SuperBuilder;

import static lombok.AccessLevel.PRIVATE;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({ "appHost", "uiHost" })
@JsonDeserialize(using = None.class)
@NoArgsConstructor
@AllArgsConstructor(access = PRIVATE)
@SuperBuilder(toBuilder = true)
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class Info implements KubernetesResource {

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
