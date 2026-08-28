package io.apicurio.registry.operator.api.v1.spec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static lombok.AccessLevel.PRIVATE;

/**
 * Configuration for operator-side collection of Apicurio Registry metrics.
 */
@JsonDeserialize(using = JsonDeserializer.None.class)
@JsonInclude(NON_NULL)
@JsonPropertyOrder({"enabled", "scrapeIntervalSeconds", "prometheusRuleEnabled", "connectionPoolUtilizationThreshold",
        "errorRateThreshold", "kafkaConsumerLagThreshold"})
@NoArgsConstructor
@AllArgsConstructor(access = PRIVATE)
@SuperBuilder(toBuilder = true)
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class MetricsSpec {

    /**
     * Enable operator-side collection of Apicurio Registry metrics.
     * When enabled, the operator periodically scrapes the management interface of each application Pod
     * and reports a summary in `status.metrics`. Default is false.
     */
    @JsonProperty("enabled")
    @JsonPropertyDescription("""
            Enable operator-side collection of Apicurio Registry metrics.
            When enabled, the operator periodically scrapes the management interface of each application Pod
            and reports a summary in `status.metrics`. Default is false.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private Boolean enabled;

    /**
     * How often the operator collects metrics, in seconds. Default is 60.
     */
    @JsonProperty("scrapeIntervalSeconds")
    @JsonPropertyDescription("""
            How often the operator collects metrics, in seconds. Default is 60.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private Integer scrapeIntervalSeconds;

    /**
     * Emit a Kubernetes Event when the fraction of in-use database connections reaches this value.
     * Value between 0.0 and 1.0. Default is 0.8.
     */
    @JsonProperty("connectionPoolUtilizationThreshold")
    @JsonPropertyDescription("""
            Emit a Kubernetes Event when the fraction of in-use database connections reaches this value.
            Value between 0.0 and 1.0. Default is 0.8.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private Double connectionPoolUtilizationThreshold;

    /**
     * Emit a Kubernetes Event when the fraction of REST API requests answered with a 5xx status
     * reaches this value. Value between 0.0 and 1.0. Default is 0.1.
     */
    @JsonProperty("errorRateThreshold")
    @JsonPropertyDescription("""
            Emit a Kubernetes Event when the fraction of REST API requests answered with a 5xx status
            reaches this value. Value between 0.0 and 1.0. Default is 0.1.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private Double errorRateThreshold;

    /**
     * Emit a Kubernetes Event when the KafkaSQL consumer lag reaches this number of records.
     * Only applies when KafkaSQL storage is used. Default is 1000.
     */
    @JsonProperty("kafkaConsumerLagThreshold")
    @JsonPropertyDescription("""
            Emit a Kubernetes Event when the KafkaSQL consumer lag reaches this number of records.
            Only applies when KafkaSQL storage is used. Default is 1000.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private Long kafkaConsumerLagThreshold;

    /**
     * Generate a PrometheusRule carrying the same thresholds, for clusters running the Prometheus Operator.
     */
    @JsonProperty("prometheusRuleEnabled")
    @JsonPropertyDescription("""
            Generate a PrometheusRule carrying the same thresholds, so that a cluster running the Prometheus
            Operator raises the same alerts through its own alerting path. Requires the Prometheus Operator
            CRDs, and something already scraping Apicurio Registry. Default is false.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private Boolean prometheusRuleEnabled;

}
