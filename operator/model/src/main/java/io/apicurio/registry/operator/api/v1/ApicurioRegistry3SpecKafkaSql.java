package io.apicurio.registry.operator.api.v1;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.JsonDeserializer.None;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import lombok.*;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({ "bootstrapServers", "security" })
@JsonDeserialize(using = None.class)
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Getter
@Setter
@ToString
public class ApicurioRegistry3SpecKafkaSql implements KubernetesResource {

    /**
     * Configure Kafka bootstrap servers.
     */
    @JsonProperty("bootstrapServers")
    @JsonPropertyDescription("""
            Configure Kafka bootstrap servers.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private String bootstrapServers;

    /**
     * Provide the following configuration options if your Kafka cluster is secured using TLS or SCRAM.
     */
    @JsonProperty("security")
    @JsonPropertyDescription("""
            Provide the following configuration options if your Kafka cluster is secured using TLS or SCRAM.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private ApicurioRegistry3SpecKafkaSqlSecurity security;
}
