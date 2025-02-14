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
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.fasterxml.jackson.annotation.Nulls.SKIP;
import static lombok.AccessLevel.PRIVATE;

@JsonDeserialize(using = None.class)
@JsonInclude(NON_NULL)
@JsonPropertyOrder({ "bootstrapServers", "tls" })
@NoArgsConstructor
@AllArgsConstructor(access = PRIVATE)
@SuperBuilder(toBuilder = true)
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class KafkaSqlSpec {

    /**
     * Configure Kafka bootstrap servers.
     * <p>
     * Required if <code>app.storage.type</code> is <code>kafkasql</code>.
     */
    @JsonProperty("bootstrapServers")
    @JsonPropertyDescription("""
            Configure Kafka bootstrap servers.

            Required if `app.storage.type` is `kafkasql`.""")
    @JsonSetter(nulls = SKIP)
    private String bootstrapServers;

    /**
     * Configure KafkaSQL storage when the access to the Kafka cluster is secured using TLS.
     */
    @JsonProperty("tls")
    @JsonPropertyDescription("""
            Configure KafkaSQL storage when the access to the Kafka cluster is secured using TLS.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private KafkaSqlTLSSpec tls;

    /**
     * Configure KafkaSQL storage when the access to the Kafka cluster is secured using TLS.
     */
    @JsonProperty("auth")
    @JsonPropertyDescription("""
            Configure KafkaSQL storage authentication.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private KafkaSqlAuthSpec auth;
}
