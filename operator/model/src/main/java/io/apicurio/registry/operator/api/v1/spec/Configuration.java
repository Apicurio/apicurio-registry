package io.apicurio.registry.operator.api.v1.spec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "env" })
@JsonDeserialize(using = JsonDeserializer.None.class)
@Getter
@Setter
public class Configuration implements KubernetesResource {

    /**
     * Environment variables: List of additional environment variables that will be provided to the Apicurio
     * Registry application.
     */
    @JsonProperty("env")
    @JsonPropertyDescription("Environment variables: \n List of additional environment variables that will be provided to the Apicurio Registry application.")
    @JsonSetter(nulls = com.fasterxml.jackson.annotation.Nulls.SKIP)
    private List<EnvVar> env;

}
