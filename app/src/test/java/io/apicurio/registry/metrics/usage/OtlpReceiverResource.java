package io.apicurio.registry.metrics.usage;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.util.Map;

/**
 * Starts the test-owned OTLP receiver before the application starts and points the OpenTelemetry exporter at
 * it, so the endpoint is the port the receiver really holds. It stops the receiver, with its executor, when
 * the application stops. Tests get the receiver through a field marked {@link InjectOtlpReceiver}.
 */
public class OtlpReceiverResource implements QuarkusTestResourceLifecycleManager {

    private OtlpMetricsTestReceiver receiver;

    @Override
    public Map<String, String> start() {
        receiver = OtlpMetricsTestReceiver.startNew(OtlpMetricsTestReceiver.Mode.OK);
        return Map.of("quarkus.otel.exporter.otlp.endpoint", receiver.endpoint());
    }

    @Override
    public void inject(TestInjector testInjector) {
        testInjector.injectIntoFields(receiver, new TestInjector.AnnotatedAndMatchesType(InjectOtlpReceiver.class,
                OtlpMetricsTestReceiver.class));
    }

    @Override
    public void stop() {
        if (receiver != null) {
            receiver.stop();
        }
    }
}
