package io.apicurio.registry.metrics;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder;
import io.opentelemetry.sdk.metrics.InstrumentSelector;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder;
import io.opentelemetry.sdk.metrics.View;
import io.opentelemetry.sdk.resources.Resource;
import io.quarkus.opentelemetry.runtime.AutoConfiguredOpenTelemetrySdkBuilderCustomizer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;

/**
 * Adjusts the OpenTelemetry SDK that Quarkus builds, but only when the usage metric is enabled:
 * <ul>
 * <li>a View that caps the number of series of the schema fetch counter, so the label values supplied by
 * clients cannot grow memory without bound;</li>
 * <li>a random {@code service.instance.id} when neither Quarkus nor the operator provided one, so a
 * restarted Registry whose in-memory database reuses IDs does not continue the series of its predecessor.</li>
 * </ul>
 * Quarkus only calls customizers while the SDK is enabled.
 */
@ApplicationScoped
public class UsageMetricsCustomizer implements AutoConfiguredOpenTelemetrySdkBuilderCustomizer {

    static final AttributeKey<String> SERVICE_INSTANCE_ID = AttributeKey.stringKey("service.instance.id");

    @Inject
    UsageMetricsConfig config;

    @Override
    public void customize(AutoConfiguredOpenTelemetrySdkBuilder builder) {
        if (!config.isEnabled()) {
            return;
        }
        int maxSeries = config.getMaxSeries();
        builder.addMeterProviderCustomizer((meterProvider, properties) -> registerCardinalityView(meterProvider,
                maxSeries));
        builder.addResourceCustomizer((resource, properties) -> withInstanceId(resource));
    }

    static SdkMeterProviderBuilder registerCardinalityView(SdkMeterProviderBuilder meterProvider, int maxSeries) {
        return meterProvider.registerView(
                InstrumentSelector.builder()
                        .setType(InstrumentType.COUNTER)
                        .setMeterName(OTelMetricsProvider.INSTRUMENTATION_NAME)
                        .setName(OTelMetricsProvider.SCHEMA_FETCHES_METRIC_NAME)
                        .build(),
                View.builder().setCardinalityLimit(maxSeries).build());
    }

    static Resource withInstanceId(Resource resource) {
        if (resource.getAttribute(SERVICE_INSTANCE_ID) != null) {
            return resource;
        }
        return resource.merge(Resource.create(Attributes.of(SERVICE_INSTANCE_ID, UUID.randomUUID().toString())));
    }
}
