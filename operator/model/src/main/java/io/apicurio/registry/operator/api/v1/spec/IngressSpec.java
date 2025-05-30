package io.apicurio.registry.operator.api.v1.spec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.JsonDeserializer.None;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.fasterxml.jackson.annotation.Nulls.SKIP;
import static lombok.AccessLevel.PRIVATE;

@JsonDeserialize(using = None.class)
@JsonInclude(NON_NULL)
@JsonPropertyOrder(alphabetic = true)
@NoArgsConstructor
@AllArgsConstructor(access = PRIVATE)
@SuperBuilder(toBuilder = true)
@Getter
@Setter
@ToString
public class IngressSpec {

    /**
     * Indicates whether to create and manage an Ingress for the component
     */
    @JsonProperty("enabled")
    @JsonPropertyDescription("""
            Whether an Ingress should be managed by the operator.  Defaults to 'true'.

            Set this to 'false' if you want to create your own custom Ingress.
            """)
    @JsonSetter(nulls = Nulls.SKIP)
    private Boolean enabled;

    /**
     * Configure hostname of the operator-managed Ingress. If the value is empty, the operator will not create
     * an Ingress resource for the component.
     * <p>
     * <b>IMPORTANT:</b> If the Ingress already exists and the value becomes empty, the Ingress will be
     * deleted.
     */
    @JsonProperty("host")
    @JsonPropertyDescription("""
            Configure hostname of the operator-managed Ingress. If the value is empty, \
            the operator will not create an Ingress resource for the component.

            IMPORTANT: If the Ingress already exists and the value becomes empty, the Ingress will be deleted.""")
    @JsonSetter(nulls = SKIP)
    private String host;

    /**
     * Additional annotations for the operator-managed Ingress.
     */
    @JsonProperty("annotations")
    @JsonPropertyDescription("""
            Additional annotations for the operator-managed Ingress.""")
    @JsonSetter(nulls = SKIP)
    @JsonInclude(NON_EMPTY)
    private Map<String, String> annotations = new LinkedHashMap<>();

    /**
     * Name of an IngressClass cluster resource.
     * Ingress controller implementations use this field to know
     * whether they should be serving this Ingress resource,
     * by a transitive connection (controller -> IngressClass -> Ingress resource).
     * <p>
     * See <a href="https://kubernetes.io/docs/concepts/services-networking/ingress/#ingress-class">Ingress class</a>.
     */
    @JsonProperty("ingressClassName")
    @JsonPropertyDescription("""
            Name of an IngressClass cluster resource. \
            Ingress controller implementations use this field to know \
            whether they should be serving this Ingress resource, \
            by a transitive connection (controller -> IngressClass -> Ingress resource).

            See https://kubernetes.io/docs/concepts/services-networking/ingress/#ingress-class.""")
    @JsonSetter(nulls = SKIP)
    private String ingressClassName;
}
