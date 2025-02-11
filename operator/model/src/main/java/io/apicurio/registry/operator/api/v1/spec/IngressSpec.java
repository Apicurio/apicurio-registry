package io.apicurio.registry.operator.api.v1.spec;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.JsonDeserializer.None;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.*;
import lombok.experimental.SuperBuilder;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.fasterxml.jackson.annotation.Nulls.SKIP;
import static lombok.AccessLevel.PRIVATE;

@JsonDeserialize(using = None.class)
@JsonInclude(NON_NULL)
@JsonPropertyOrder({ "enabled", "host" })
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
}
