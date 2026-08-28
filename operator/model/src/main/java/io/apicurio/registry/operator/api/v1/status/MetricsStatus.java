package io.apicurio.registry.operator.api.v1.status;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.JsonDeserializer.None;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.fabric8.crd.generator.annotation.SchemaFrom;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

import static lombok.AccessLevel.PRIVATE;

/**
 * Metrics collected by the operator from the Apicurio Registry application Pods.
 * <p>
 * Present only when `spec.app.metrics.enabled` is true.
 */
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({"lastCollected", "summary"})
@JsonDeserialize(using = None.class)
@NoArgsConstructor
@AllArgsConstructor(access = PRIVATE)
@SuperBuilder(toBuilder = true)
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class MetricsStatus {

    /**
     * The last time the reported summary changed.
     * <p>
     * This is deliberately not the time of the last collection attempt. Refreshing it on every attempt
     * would rewrite the resource on every reconciliation, which would in turn trigger another
     * reconciliation.
     */
    @JsonProperty("lastCollected")
    @JsonPropertyDescription("""
            The last time the reported summary changed. This is not the time of the last collection attempt.""")
    @JsonSetter(nulls = Nulls.SKIP)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    @SchemaFrom(type = String.class)
    private Instant lastCollected;

    /**
     * Aggregated metric values across all application Pods.
     */
    @JsonProperty("summary")
    @JsonPropertyDescription("""
            Aggregated metric values across all application Pods.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private MetricsSummary summary;

}
