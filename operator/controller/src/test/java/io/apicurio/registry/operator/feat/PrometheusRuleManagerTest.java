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

    /**
     * The rule is owned by the custom resource that asked for it, so deleting the Registry takes the rule
     * with it even when the operator is not running at the time. A rule left behind keeps alerting for a
     * deployment that no longer exists.
     */
    @Test
    public void testTheRuleIsOwnedByTheCustomResource() {
        var registry = registry(new MetricsSpec());
        registry.getMetadata().setUid("4b1d0000-0000-0000-0000-00000000cafe");
        registry.setApiVersion("registry.apicur.io/v1");
        registry.setKind("ApicurioRegistry3");

        assertThat(PrometheusRuleManager.ownerReferences(registry)).singleElement().satisfies(owner -> {
            assertThat(owner.getUid()).isEqualTo("4b1d0000-0000-0000-0000-00000000cafe");
            assertThat(owner.getName()).isEqualTo("rules");
            assertThat(owner.getKind()).isEqualTo("ApicurioRegistry3");
            assertThat(owner.getApiVersion()).isEqualTo("registry.apicur.io/v1");
            assertThat(owner.getController()).isTrue();
        });
    }

    /**
     * The API server rejects an owner reference without a UID, so a resource that does not have one yet must
     * produce no reference at all rather than an invalid one.
     */
    @Test
    public void testNoOwnerReferenceWithoutAUid() {
        assertThat(PrometheusRuleManager.ownerReferences(registry(new MetricsSpec()))).isEmpty();
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
