package io.apicurio.registry.operator.api.v1;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import lombok.Getter;
import lombok.Setter;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "configuration", "deployment" })
@JsonDeserialize(using = JsonDeserializer.None.class)
@Getter
@Setter
public class ApicurioRegistry3Spec implements KubernetesResource {
}
