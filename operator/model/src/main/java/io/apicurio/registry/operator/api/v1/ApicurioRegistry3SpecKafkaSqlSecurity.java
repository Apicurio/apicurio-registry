package io.apicurio.registry.operator.api.v1;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.JsonDeserializer.None;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import lombok.*;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({ "tls" })
@JsonDeserialize(using = None.class)
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Getter
@Setter
@ToString
public class ApicurioRegistry3SpecKafkaSqlSecurity implements KubernetesResource {

    /**
     * Kafka is secured using TLS.
     */
    @JsonProperty("tls")
    @JsonPropertyDescription("""
            Kafka is secured using TLS.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private ApicurioRegistry3SpecKafkaSqlTLS tls;
}
