package io.apicurio.registry.operator.api.v1;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.JsonDeserializer.None;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.apicurio.registry.operator.api.v1.spec.Sql;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({ "sql", "kafkasql", "env", "host" })
@JsonDeserialize(using = None.class)
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Getter
@Setter
@ToString
public class ApicurioRegistry3SpecApp implements KubernetesResource {

    @JsonProperty("sql")
    @JsonPropertyDescription("""
            Configuration of Apicurio Registry SQL storage.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private Sql sql;

    @JsonProperty("kafkasql")
    @JsonPropertyDescription("""
            Configure KafkaSQL storage.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private ApicurioRegistry3SpecKafkaSql kafkasql;

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
}
