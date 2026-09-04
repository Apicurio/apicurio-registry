package io.apicurio.registry.operator.metrics;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.spec.InsecureRequests;
import io.apicurio.registry.operator.api.v1.spec.SecretKeyRef;
import io.apicurio.registry.operator.api.v1.spec.TLSSpec;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class MetricsCollectorTest {

    private static final String POD_A = """
            rest_requests_seconds_count{status_code_group="2xx"} 100
            rest_requests_seconds_count{status_code_group="5xx"} 5
            agroal_active_count 8
            agroal_available_count 2
            storage_artifacts 40
            storage_artifact_versions 120
            kafka_consumer_fetch_manager_records_lag_max 300
            """;

    private static final String POD_B = """
            rest_requests_seconds_count{status_code_group="2xx"} 50
            agroal_active_count 2
            agroal_available_count 8
            storage_artifacts 40
            storage_artifact_versions 120
            kafka_consumer_fetch_manager_records_lag_max 700
            """;

    @Test
    public void testAggregatesAcrossPods() {
        var snapshot = MetricsCollector.aggregate(bodies("pod-a", POD_A, "pod-b", POD_B));

        assertThat(snapshot.scrapedPods()).isEqualTo(2);
        assertThat(snapshot.kafkaConsumerLag()).isEqualTo(700L);
        // Every replica counts the same shared storage, so these are maxed and not summed.
        assertThat(snapshot.artifactCount()).isEqualTo(40L);
        assertThat(snapshot.artifactVersionCount()).isEqualTo(120L);
    }

    /**
     * Request counters stay attributed to the Pod they came from. Summing them here would make a scrape that
     * covered a different set of Pods indistinguishable from one that covered the same set, and the operator
     * derives rates by differencing these.
     */
    @Test
    public void testRequestCountersAreKeptPerPod() {
        var snapshot = MetricsCollector.aggregate(bodies("pod-a", POD_A, "pod-b", POD_B));

        assertThat(snapshot.requestMetricSeen()).isTrue();
        assertThat(snapshot.requestCounters()).containsOnlyKeys("pod-a", "pod-b");
        assertThat(snapshot.requestCounters().get("pod-a").requests()).isEqualTo(105.0);
        assertThat(snapshot.requestCounters().get("pod-a").serverErrors()).isEqualTo(5.0);
        assertThat(snapshot.requestCounters().get("pod-b").requests()).isEqualTo(50.0);
        assertThat(snapshot.requestCounters().get("pod-b").serverErrors()).isEqualTo(0.0);
    }

    /**
     * A replica whose pool is exhausted is already queueing or failing requests. Averaging across replicas
     * would let it hide behind idle ones, which is the case the threshold exists to catch.
     */
    @Test
    public void testPoolUtilizationReportsTheWorstPodRatherThanTheAverage() {
        // Pod A is at 8/10 and Pod B at 2/10, which would average to 0.5.
        var snapshot = MetricsCollector.aggregate(bodies("pod-a", POD_A, "pod-b", POD_B));

        assertThat(snapshot.poolUtilization()).isEqualTo(0.8);
    }

    @Test
    public void testAbsentMetricsAreReportedAsAbsentRatherThanZero() {
        var snapshot = MetricsCollector.aggregate(bodies("pod-a", """
                some_unrelated_metric 1
                kafka_consumer_fetch_manager_records_lag_max NaN
                """));

        assertThat(snapshot.requestMetricSeen()).isFalse();
        assertThat(snapshot.requestCounters()).isEmpty();
        assertThat(snapshot.poolUtilization()).isNull();
        assertThat(snapshot.artifactCount()).isNull();
        // Non-finite samples are skipped rather than turned into a bogus value.
        assertThat(snapshot.kafkaConsumerLag()).isNull();
    }

    /**
     * Registry promotes individual status codes to their own group when they appear in
     * apicurio.metrics.rest.explicit-status-codes-list, so matching only "5xx" would miss them.
     */
    @Test
    public void testExplicitServerErrorCodesAreCounted() {
        var snapshot = MetricsCollector.aggregate(bodies("pod-a", """
                rest_requests_seconds_count{status_code_group="2xx"} 90
                rest_requests_seconds_count{status_code_group="503"} 10
                rest_requests_seconds_count{status_code_group="401"} 5
                """));

        assertThat(snapshot.requestCounters().get("pod-a").requests()).isEqualTo(105.0);
        assertThat(snapshot.requestCounters().get("pod-a").serverErrors()).isEqualTo(10.0);
    }

    /**
     * Parses output captured from a real Registry rather than a hand-written approximation, so a change in
     * how the operand names or labels these metrics is caught here.
     */
    @Test
    public void testOutputCapturedFromARealRegistry() throws Exception {
        String body;
        try (var in = getClass().getResourceAsStream("/metrics/registry-sql-metrics.txt")) {
            body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        var snapshot = MetricsCollector.aggregate(bodies("pod-a", body));

        // 7 successful and 3 not-found requests, counted once each across all label combinations.
        assertThat(snapshot.requestCounters().get("pod-a").requests()).isEqualTo(10.0);
        assertThat(snapshot.requestCounters().get("pod-a").serverErrors()).isEqualTo(0.0);
        // The pool had opened 20 connections and none were checked out.
        assertThat(snapshot.poolUtilization()).isEqualTo(0.0);
        // Captured from a released image, which predates the gauges this change adds to the app module.
        assertThat(snapshot.kafkaConsumerLag()).isNull();
        assertThat(snapshot.artifactCount()).isNull();
    }

    /**
     * Configuring a keystore moves the Quarkus management interface to HTTPS, so the plain HTTP scrape cannot
     * work. Allowing insecure requests does not bring it back, that only affects the API port.
     */
    @Test
    public void testTlsInstanceIsSkippedWithAnExplanation() {
        assertThat(MetricsCollector.servesManagementOverTls(registryWithTls(null))).isFalse();

        var tls = new TLSSpec();
        tls.setTruststoreSecretRef(new SecretKeyRef());
        assertThat(MetricsCollector.servesManagementOverTls(registryWithTls(tls))).isFalse();

        tls.setKeystoreSecretRef(new SecretKeyRef());
        tls.setInsecureRequests(InsecureRequests.ENABLED);
        assertThat(MetricsCollector.servesManagementOverTls(registryWithTls(tls))).isTrue();

        assertThatThrownBy(() -> new MetricsCollector().collect(null, registryWithTls(tls)))
                .isInstanceOf(MetricsCollectionException.class)
                .hasMessageContaining("spec.app.tls");
    }

    private static Map<String, String> bodies(String name, String body) {
        var bodies = new LinkedHashMap<String, String>();
        bodies.put(name, body);
        return bodies;
    }

    private static Map<String, String> bodies(String firstName, String firstBody, String secondName,
                                              String secondBody) {
        var bodies = bodies(firstName, firstBody);
        bodies.put(secondName, secondBody);
        return bodies;
    }

    private static ApicurioRegistry3 registryWithTls(TLSSpec tls) {
        var registry = new ApicurioRegistry3();
        registry.setMetadata(new ObjectMetaBuilder().withName("tls-test").withNamespace("test").build());
        registry.withSpec().withApp().setTls(tls);
        return registry;
    }
}
