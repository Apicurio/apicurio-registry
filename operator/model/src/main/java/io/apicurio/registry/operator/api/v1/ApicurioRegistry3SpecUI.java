package io.apicurio.registry.operator.api.v1;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.JsonDeserializer.None;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({ "env", "host", "podTemplateSpec" })
@JsonDeserialize(using = None.class)
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Getter
@Setter
@ToString
public class ApicurioRegistry3SpecUI implements KubernetesResource {

    @JsonProperty("env")
    @JsonPropertyDescription("""
            List of environment variables that should be passed to the UI component.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private List<EnvVar> env = new ArrayList<>();

    /**
     * Apicurio Registry UI component hostname. If the value is empty, the Operator will not create an Ingress
     * resource for the component. IMPORTANT: If the Ingress already exists and the value becomes empty, the
     * Ingress will be deleted.
     */
    @JsonProperty("host")
    @JsonPropertyDescription("""
            Apicurio Registry UI component hostname.
            If the value is empty, the Operator will not create an Ingress resource for the component.
            IMPORTANT: If the Ingress already exists and the value becomes empty, the Ingress will be deleted.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private String host;

    /**
     * This field can be used to specify a PodTemplateSpec that will be used to generate Deployment for the UI
     * component. This allows users to modify the apicurio-registry-ui container, or adding another container
     * to the pod. Operator will apply changes on top of this PTS, so some parts might be overridden,
     * depending on other fields in this CR. Restrictions: `.spec.containers[name = apicurio-registry-ui].env`
     * must be empty.""")
     */
    @JsonProperty("podTemplateSpec")
    @JsonPropertyDescription("""
            This field can be used to specify a PodTemplateSpec that will be used to generate Deployment for the UI component.
            This allows users to modify the apicurio-registry-ui container, or adding another container to the pod.
            Operator will apply changes on top of this PTS, so some parts might be overridden, depending on other fields in this CR.
            Restrictions: `.spec.containers[name = apicurio-registry-ui].env` must be empty.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private PodTemplateSpec podTemplateSpec;
}
