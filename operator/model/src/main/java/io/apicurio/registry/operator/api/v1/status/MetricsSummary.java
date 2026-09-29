package io.apicurio.registry.operator.api.v1.status;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
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

import static lombok.AccessLevel.PRIVATE;

/**
 * Aggregated values collected from the management interface of the Apicurio Registry application Pods.
 * <p>
 * Every field is optional. A field is absent when the underlying metric is not exposed by the operand,
 * which is preferable to reporting a misleading zero.
 */
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({"requestRate", "errorRate", "connectionPoolUtilization", "artifactCount",
        "artifactVersionCount", "kafkaConsumerLag", "scrapedPods"})
@JsonDeserialize(using = None.class)
@NoArgsConstructor
@AllArgsConstructor(access = PRIVATE)
@SuperBuilder(toBuilder = true)
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class MetricsSummary {

    /**
     * REST API requests per second, averaged over the interval between the last two collections,
     * summed across all application Pods.
     */
    @JsonProperty("requestRate")
    @JsonPropertyDescription("""
            REST API requests per second, averaged over the interval between the last two collections,
            summed across all application Pods.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private Double requestRate;

    /**
     * Fraction of REST API requests answered with a 5xx status, over the interval between the last two
     * collections. Value between 0.0 and 1.0.
     */
    @JsonProperty("errorRate")
    @JsonPropertyDescription("""
            Fraction of REST API requests answered with a 5xx status, over the interval between the last two
            collections. Value between 0.0 and 1.0.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private Double errorRate;

    /**
     * Highest fraction of the database connection pool in use on any one application Pod. Reported as the
     * highest rather than the average, because a replica whose pool is exhausted is already queueing or
     * failing requests, and averaging would hide it behind idle replicas. Value between 0.0 and 1.0.
     */
    @JsonProperty("connectionPoolUtilization")
    @JsonPropertyDescription("""
            Highest fraction of the database connection pool in use on any one application Pod. Reported as
            the highest rather than the average, so that an exhausted replica is not hidden by idle ones.
            Value between 0.0 and 1.0.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private Double connectionPoolUtilization;

    /**
     * Number of artifacts currently held in storage.
     */
    @JsonProperty("artifactCount")
    @JsonPropertyDescription("""
            Number of artifacts currently held in storage.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private Long artifactCount;

    /**
     * Number of artifact versions currently held in storage.
     */
    @JsonProperty("artifactVersionCount")
    @JsonPropertyDescription("""
            Number of artifact versions currently held in storage.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private Long artifactVersionCount;

    /**
     * Highest KafkaSQL consumer lag, in records, reported by any application Pod.
     * Only present when KafkaSQL storage is used.
     */
    @JsonProperty("kafkaConsumerLag")
    @JsonPropertyDescription("""
            Highest KafkaSQL consumer lag, in records, reported by any application Pod.
            Only present when KafkaSQL storage is used.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private Long kafkaConsumerLag;

    /**
     * How many application Pods these values were read from.
     * <p>
     * The operator reads at most 10 Pods per collection, so that one custom resource cannot turn a single
     * reconciliation into an unbounded number of requests. Above that, and whenever a Pod cannot be reached,
     * the totals here cover only the Pods that answered. This field says how many that was, so that
     * `requestRate` is not read as the whole deployment when it is not.
     */
    @JsonProperty("scrapedPods")
    @JsonPropertyDescription("""
            How many application Pods these values were read from. The operator reads at most 10 Pods per
            collection, so on a larger deployment, or when a Pod cannot be reached, the totals cover only
            the Pods that answered.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private Integer scrapedPods;

}
