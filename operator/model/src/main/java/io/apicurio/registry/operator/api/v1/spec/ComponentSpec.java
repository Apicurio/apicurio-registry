package io.apicurio.registry.operator.api.v1.spec;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.JsonDeserializer.None;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.apicurio.registry.operator.api.v1.ContainerNames;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.fasterxml.jackson.annotation.Nulls.SKIP;
import static lombok.AccessLevel.PROTECTED;

@JsonDeserialize(using = None.class)
@JsonInclude(NON_NULL)
@JsonPropertyOrder({ "env", "ingress", "host", "podTemplateSpec" })
@NoArgsConstructor(access = PROTECTED)
@AllArgsConstructor(access = PROTECTED)
@SuperBuilder(toBuilder = true)
@Getter
@Setter
@EqualsAndHashCode
@ToString
public abstract class ComponentSpec {

    /**
     * Configure a list of environment variables that will be passed to this components' container.
     *
     * @see <a href=
     *      "https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.32/#envvar-v1-core">Kubernetes
     *      documentation</a>
     */
    @JsonProperty("env")
    @JsonPropertyDescription("""
            Configure a list of environment variables that will be passed to this components' container.""")
    @JsonInclude(NON_EMPTY)
    @JsonSetter(nulls = SKIP)
    private List<EnvVar> env = new ArrayList<>();

    /**
     * Configure Ingress for the component.
     */
    @JsonProperty("ingress")
    @JsonPropertyDescription("""
            Configure Ingress for the component.""")
    @JsonSetter(nulls = SKIP)
    private IngressSpec ingress;

    /**
     * <code>PodTemplateSpec</code> describes the data a pod should have when created from a template.
     * <p>
     * This template is used by the operator to create the components' Deployment. The operator first extends
     * the template with default values if required, and then applies additional configuration based on the
     * other contents of <code>ApicurioRegistry3</code> CR.
     *
     * @see <a href=
     *      "https://kubernetes.io/docs/reference/kubernetes-api/workload-resources/pod-template-v1/#PodTemplateSpec">Kubernetes
     *      documentation</a>
     * @see ContainerNames
     */
    @JsonProperty("podTemplateSpec")
    @JsonPropertyDescription("""
            `PodTemplateSpec` describes the data a pod should have when created from a template.

            This template is used by the operator to create the components' Deployment. The operator first extends the template
            with default values if required, and then applies additional configuration
            based on the other contents of `ApicurioRegistry3` CR.""")
    @JsonSetter(nulls = SKIP)
    private PodTemplateSpec podTemplateSpec;

    /**
     * @deprecated Use the `(component).ingress.host` field instead. The operator will attempt to update the
     *             field automatically.
     */
    @JsonProperty("host")
    @JsonPropertyDescription("""
            DEPRECATED: Use the `(component).ingress.host` field instead. The operator will attempt to update the field automatically.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private String host;

    /**
     * Number of replicas for the component
     */
    @JsonProperty("replicas")
    @JsonPropertyDescription("Number of replicas for the component")
    @JsonSetter(nulls = Nulls.SKIP)
    private Integer replicas;

    /**
     * Indicates whether to create and manage an Ingress for the component
     */
    @JsonProperty("manageIngress")
    @JsonPropertyDescription("""
            Whether an Ingress should be managed by the operator.  Defaults to 'true'.
            Set this to 'false' if you want to create your own custom Ingress.
            """)
    @JsonSetter(nulls = Nulls.SKIP)
    private Boolean manageIngress;

    public IngressSpec withIngress() {
        if (ingress == null) {
            ingress = new IngressSpec();
        }
        return ingress;
    }
}
