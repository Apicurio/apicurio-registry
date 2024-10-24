package io.apicurio.registry.operator.api.v1;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.JsonDeserializer.None;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.apicurio.registry.operator.api.v1.spec.Sql;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({ "env", "host", "sql", "podTemplateSpec" })
@JsonDeserialize(using = None.class)
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Getter
@Setter
@ToString
public class ApicurioRegistry3SpecApp implements KubernetesResource {

    @JsonProperty("env")
    @JsonPropertyDescription("""
            List of environment variables that should be passed to the App component.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private List<EnvVar> env = new ArrayList<>();

    /**
     * Apicurio Registry backend component hostname. If the value is empty, the Operator will not create an
     * Ingress resource for the component. IMPORTANT: If the Ingress already exists and the value becomes
     * empty, the Ingress will be deleted.
     * <p>
     * Note that the UI component requires a browser-accessible URL to the Apicurio Registry backend to work
     * properly. If you create the Ingress manually, you have to manually set the REGISTRY_API_URL environment
     * variable for the backend component.
     */
    @JsonProperty("host")
    @JsonPropertyDescription("""
            Apicurio Registry backend component hostname.
            If the value is empty, the Operator will not create an Ingress resource for the component.
            IMPORTANT: If the Ingress already exists and the value becomes empty, the Ingress will be deleted.

            Note that the UI component requires a browser-accessible URL to the Apicurio Registry backend to work properly.
            If you create the Ingress manually, you have to manually set the REGISTRY_API_URL environment variable for the backend component.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private String host;

    @JsonProperty("sql")
    @JsonPropertyDescription("""
            Configuration of Apicurio Registry SQL storage.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private Sql sql;

    /**
     * This field can be used to specify a PodTemplateSpec that will be used to generate Deployment for the
     * App component This allows users to modify the apicurio-registry-app container, or adding another
     * container to the pod. Operator will apply changes on top of this PTS, so some parts might be
     * overridden, depending on other fields in this CR. Restrictions: `.spec.containers[name =
     * apicurio-registry-app].env` must be empty.
     */
    @JsonProperty("podTemplateSpec")
    @JsonPropertyDescription("""
            This field can be used to specify a PodTemplateSpec that will be used to generate Deployment for the App component
            This allows users to modify the apicurio-registry-app container, or adding another container to the pod.
            Operator will apply changes on top of this PTS, so some parts might be overridden, depending on other fields in this CR.
            Restrictions: `.spec.containers[name = apicurio-registry-app].env` must be empty.
            """)
    @JsonSetter(nulls = Nulls.SKIP)
    private PodTemplateSpec podTemplateSpec;
}
