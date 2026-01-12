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
 * Configuration for OpenTelemetry observability in Apicurio Registry.
 */
@JsonDeserialize(using = JsonDeserializer.None.class)
@JsonInclude(NON_NULL)
@JsonPropertyOrder({"enabled", "endpoint", "protocol", "traceSamplingRatio"})
@NoArgsConstructor
@AllArgsConstructor(access = PRIVATE)
@SuperBuilder(toBuilder = true)
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class OTelSpec {

    /**
     * Enable OpenTelemetry for distributed tracing, metrics export via OTLP, and log correlation.
     * When enabled, Apicurio Registry exports telemetry data to an OpenTelemetry collector.
     * Default is false.
     */
    @JsonProperty("enabled")
    @JsonPropertyDescription("""
            Enable OpenTelemetry for distributed tracing, metrics export via OTLP, and log correlation.
            When enabled, Apicurio Registry exports telemetry data to an OpenTelemetry collector.
            Default is false.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private Boolean enabled;

    /**
     * The endpoint URL of the OpenTelemetry collector.
     * Supports both gRPC (typically port 4317) and HTTP (typically port 4318) protocols.
     * Example: http://jaeger:4317 or http://otel-collector:4317
     */
    @JsonProperty("endpoint")
    @JsonPropertyDescription("""
            The endpoint URL of the OpenTelemetry collector.
            Supports both gRPC (typically port 4317) and HTTP (typically port 4318) protocols.
            Example: http://jaeger:4317 or http://otel-collector:4317""")
    @JsonSetter(nulls = Nulls.SKIP)
    private String endpoint;

    /**
     * The protocol to use for exporting telemetry data.
     * Valid values are 'grpc' or 'http/protobuf'. Default is 'grpc'.
     */
    @JsonProperty("protocol")
    @JsonPropertyDescription("""
            The protocol to use for exporting telemetry data.
            Valid values are 'grpc' or 'http/protobuf'. Default is 'grpc'.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private String protocol;

    /**
     * The sampling ratio for traces when using ratio-based sampling.
     * Value between 0.0 and 1.0. A value of 0.1 means 10% of traces are sampled.
     * When set, uses 'parentbased_traceidratio' sampler. When not set, uses 'parentbased_always_on'.
     * Recommended to set a value (e.g., 0.1) for production deployments.
     */
    @JsonProperty("traceSamplingRatio")
    @JsonPropertyDescription("""
            The sampling ratio for traces when using ratio-based sampling.
            Value between 0.0 and 1.0. A value of 0.1 means 10% of traces are sampled.
            When set, uses 'parentbased_traceidratio' sampler. When not set, uses 'parentbased_always_on'.
            Recommended to set a value (e.g., 0.1) for production deployments.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private Double traceSamplingRatio;

}
