package io.apicurio.registry.observability;

import io.apicurio.common.apps.config.Info;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_OBSERVABILITY;

/**
 * Configuration properties for OpenTelemetry observability features.
 *
 * NOTE: This class exists purely for documentation generation purposes. The properties defined here
 * are Quarkus OpenTelemetry configuration properties that are not directly injected or used by the
 * application code. They are included here to ensure they appear in the generated configuration
 * documentation (ref-registry-all-configs.adoc).
 */
@Singleton
public class OpenTelemetryConfig {

    @ConfigProperty(name = "quarkus.otel.enabled", defaultValue = "true")
    @Info(category = CATEGORY_OBSERVABILITY, description = "Enable or disable OpenTelemetry for distributed tracing, metrics export via OTLP, and log correlation. When enabled, {registry} exports telemetry data to an OpenTelemetry collector.", availableSince = "3.1.7")
    boolean otelEnabled;

    @ConfigProperty(name = "quarkus.otel.sdk.disabled", defaultValue = "true")
    @Info(category = CATEGORY_OBSERVABILITY, description = "Disable the OpenTelemetry SDK at runtime. Set to `false` to enable OpenTelemetry. This is the primary runtime control for enabling/disabling all telemetry signals.", availableSince = "3.1.7")
    boolean otelSdkDisabled;

    @ConfigProperty(name = "quarkus.otel.service.name", defaultValue = "apicurio-registry")
    @Info(category = CATEGORY_OBSERVABILITY, description = "The logical service name for this {registry} instance. This name appears in traces and metrics exported to the OpenTelemetry collector.", availableSince = "3.1.7")
    String otelServiceName;

    @ConfigProperty(name = "quarkus.otel.exporter.otlp.endpoint", defaultValue = "http://localhost:4317")
    @Info(category = CATEGORY_OBSERVABILITY, description = "The endpoint URL of the OpenTelemetry collector. Supports both gRPC (port 4317) and HTTP (port 4318) protocols.", availableSince = "3.1.7")
    String otelExporterEndpoint;

    @ConfigProperty(name = "quarkus.otel.exporter.otlp.protocol", defaultValue = "grpc")
    @Info(category = CATEGORY_OBSERVABILITY, description = "The protocol to use for exporting telemetry data. Valid values are `grpc` or `http/protobuf`.", availableSince = "3.1.7")
    String otelExporterProtocol;

    @ConfigProperty(name = "quarkus.otel.traces.enabled", defaultValue = "true")
    @Info(category = CATEGORY_OBSERVABILITY, description = "BUILD-TIME property to enable distributed tracing instrumentation. When enabled, {registry} creates spans for REST API requests and storage operations. Use `quarkus.otel.sdk.disabled` to control tracing at runtime.", availableSince = "3.1.7")
    boolean otelTracesEnabled;

    @ConfigProperty(name = "quarkus.otel.traces.sampler", defaultValue = "parentbased_always_on")
    @Info(category = CATEGORY_OBSERVABILITY, description = "The sampling strategy for traces. Use `parentbased_always_on` for development or `parentbased_traceidratio` for production to reduce overhead.", availableSince = "3.1.7")
    String otelTracesSampler;

    @ConfigProperty(name = "quarkus.otel.traces.sampler.arg", defaultValue = "1.0")
    @Info(category = CATEGORY_OBSERVABILITY, description = "The sampling ratio when using `parentbased_traceidratio` sampler. A value of `0.1` means 10% of traces are sampled.", availableSince = "3.1.7")
    String otelTracesSamplerArg;

    @ConfigProperty(name = "quarkus.otel.metrics.enabled", defaultValue = "true")
    @Info(category = CATEGORY_OBSERVABILITY, description = "BUILD-TIME property to enable metrics export via OpenTelemetry. This works alongside existing Prometheus metrics export. Use `quarkus.otel.sdk.disabled` to control metrics at runtime.", availableSince = "3.1.7")
    boolean otelMetricsEnabled;

    @ConfigProperty(name = "quarkus.otel.logs.enabled", defaultValue = "true")
    @Info(category = CATEGORY_OBSERVABILITY, description = "BUILD-TIME property to enable log export via OpenTelemetry. When enabled along with JSON logging, trace context is automatically included in log entries. Use `quarkus.otel.sdk.disabled` to control log export at runtime.", availableSince = "3.1.7")
    boolean otelLogsEnabled;

    @ConfigProperty(name = "quarkus.otel.resource.attributes", defaultValue = "")
    @Info(category = CATEGORY_OBSERVABILITY, description = "Additional resource attributes to include in telemetry data, specified as comma-separated key=value pairs. Example: `service.version=3.0.0,deployment.environment=production`", availableSince = "3.1.7")
    String otelResourceAttributes;

    @ConfigProperty(name = "quarkus.otel.instrument.kafka", defaultValue = "true")
    @Info(category = CATEGORY_OBSERVABILITY, description = "Enable or disable automatic tracing instrumentation for Kafka operations. Useful for tracing in KafkaSQL storage deployments.", availableSince = "3.1.7")
    boolean otelInstrumentKafka;

}
