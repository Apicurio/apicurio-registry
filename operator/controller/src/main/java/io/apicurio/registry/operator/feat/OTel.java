package io.apicurio.registry.operator.feat;

import io.apicurio.registry.operator.api.v1.spec.OTelSpec;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static io.apicurio.registry.operator.EnvironmentVariables.*;
import static io.apicurio.registry.operator.resource.app.AppDeploymentResource.addEnvVar;

/**
 * Configures OpenTelemetry observability for the Apicurio Registry application.
 * <p>
 * When OpenTelemetry is enabled, the operator sets environment variables to configure:
 * <ul>
 *   <li>OTLP exporter endpoint and protocol</li>
 *   <li>Trace sampling strategy and ratio</li>
 * </ul>
 */
public class OTel {

    private static final Logger log = LoggerFactory.getLogger(OTel.class);

    /**
     * Configure OpenTelemetry for the application based on the provided OTelSpec.
     *
     * @param otelSpec the OpenTelemetry specification from the CR
     * @param envVars  the environment variables map to populate
     */
    public static void configureOTel(OTelSpec otelSpec, Map<String, EnvVar> envVars) {
        if (otelSpec == null) {
            return;
        }

        Boolean enabled = otelSpec.getEnabled();
        if (enabled == null || !enabled) {
            log.debug("OpenTelemetry is not enabled");
            return;
        }

        log.info("Configuring OpenTelemetry observability");

        // Enable OpenTelemetry
        addEnvVar(envVars, new EnvVarBuilder()
                .withName(QUARKUS_OTEL_ENABLED)
                .withValue("true")
                .build());

        // Configure OTLP endpoint
        String endpoint = otelSpec.getEndpoint();
        if (endpoint != null && !endpoint.isBlank()) {
            addEnvVar(envVars, new EnvVarBuilder()
                    .withName(QUARKUS_OTEL_EXPORTER_OTLP_ENDPOINT)
                    .withValue(endpoint)
                    .build());
            log.debug("OpenTelemetry OTLP endpoint: {}", endpoint);
        }

        // Configure OTLP protocol
        String protocol = otelSpec.getProtocol();
        if (protocol != null && !protocol.isBlank()) {
            addEnvVar(envVars, new EnvVarBuilder()
                    .withName(QUARKUS_OTEL_EXPORTER_OTLP_PROTOCOL)
                    .withValue(protocol)
                    .build());
            log.debug("OpenTelemetry OTLP protocol: {}", protocol);
        }

        // Configure trace sampling
        Double samplingRatio = otelSpec.getTraceSamplingRatio();
        if (samplingRatio != null) {
            // Use ratio-based sampling
            addEnvVar(envVars, new EnvVarBuilder()
                    .withName(QUARKUS_OTEL_TRACES_SAMPLER)
                    .withValue("parentbased_traceidratio")
                    .build());
            addEnvVar(envVars, new EnvVarBuilder()
                    .withName(QUARKUS_OTEL_TRACES_SAMPLER_ARG)
                    .withValue(String.valueOf(samplingRatio))
                    .build());
            log.debug("OpenTelemetry trace sampling ratio: {}", samplingRatio);
        }
    }
}
