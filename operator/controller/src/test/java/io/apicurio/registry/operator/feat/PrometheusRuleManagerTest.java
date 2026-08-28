package io.apicurio.registry.operator.feat;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.spec.MetricsSpec;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PrometheusRuleManagerTest {

    @Test
    public void testDisabledUnlessAskedFor() {
        assertThat(PrometheusRuleManager.isEnabled(registry(null))).isFalse();

        var metrics = new MetricsSpec();
        metrics.setEnabled(true);
        assertThat(PrometheusRuleManager.isEnabled(registry(metrics))).isFalse();

        metrics.setPrometheusRuleEnabled(true);
        assertThat(PrometheusRuleManager.isEnabled(registry(metrics))).isTrue();
    }

    /**
     * The generated alerts must carry the thresholds configured on the CR, otherwise Prometheus and the
     * operator would disagree about when something is wrong.
     */
    @Test
    public void testRulesCarryTheConfiguredThresholds() {
        var metrics = new MetricsSpec();
        metrics.setEnabled(true);
        metrics.setPrometheusRuleEnabled(true);
        metrics.setConnectionPoolUtilizationThreshold(0.6);
        metrics.setKafkaConsumerLagThreshold(250L);

        var rules = PrometheusRuleManager.buildRules(registry(metrics), "my-namespace");

        assertThat(rules).hasSize(3);
        assertThat(rules).allSatisfy(rule -> assertThat(rule.get("expr").toString())
                .contains("namespace=\"my-namespace\""));
        assertThat(expr(rules, "ApicurioRegistryConnectionPoolSaturated")).endsWith(">= 0.6");
        assertThat(expr(rules, "ApicurioRegistryKafkaConsumerLagHigh")).endsWith(">= 250");
        // Not configured, so the default applies.
        assertThat(expr(rules, "ApicurioRegistryHighErrorRate")).endsWith(">= 0.1");
    }

    private static String expr(java.util.List<java.util.Map<String, Object>> rules, String alert) {
        return rules.stream().filter(r -> alert.equals(r.get("alert"))).findFirst().orElseThrow()
                .get("expr").toString();
    }

    private static ApicurioRegistry3 registry(MetricsSpec metrics) {
        var registry = new ApicurioRegistry3();
        registry.setMetadata(new ObjectMetaBuilder().withName("rules").withNamespace("test").build());
        registry.withSpec().withApp().setMetrics(metrics);
        return registry;
    }
}
