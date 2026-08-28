package io.apicurio.registry.operator.feat;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3Spec;
import io.apicurio.registry.operator.api.v1.spec.AppSpec;
import io.apicurio.registry.operator.api.v1.spec.MetricsSpec;
import io.apicurio.registry.operator.resource.Labels;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.apicurio.registry.operator.metrics.MetricsManager.DEFAULT_ERROR_RATE_THRESHOLD;
import static io.apicurio.registry.operator.metrics.MetricsManager.DEFAULT_KAFKA_CONSUMER_LAG_THRESHOLD;
import static io.apicurio.registry.operator.metrics.MetricsManager.DEFAULT_POOL_UTILIZATION_THRESHOLD;
import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_APP;
import static java.util.Optional.ofNullable;

/**
 * Generates a PrometheusRule carrying the same thresholds the operator itself watches, so that a cluster
 * already running the Prometheus Operator raises the same alerts through its normal alerting path.
 * <p>
 * The rules are evaluated over metrics Prometheus has already scraped. Nothing here arranges that scraping;
 * that is what a ServiceMonitor is for. Until one exists the generated rules are inert, which is why this is
 * opt-in and off by default.
 * <p>
 * PrometheusRule has no typed model on the operator's classpath, so it is managed as a generic resource in
 * the same way as the OpenShift ConsolePlugin.
 */
public class PrometheusRuleManager {

    private static final Logger log = LoggerFactory.getLogger(PrometheusRuleManager.class);

    private static final String MONITORING_API_GROUP = "monitoring.coreos.com";
    private static final String MONITORING_API_VERSION = "v1";
    private static final String PROMETHEUS_RULE_KIND = "PrometheusRule";
    private static final String PROMETHEUS_RULE_PLURAL = "prometheusrules";

    private static final AtomicBoolean monitoringDetected = new AtomicBoolean(false);
    private static volatile boolean detectionDone = false;

    private PrometheusRuleManager() {
    }

    public static String getRuleName(ApicurioRegistry3 primary) {
        return primary.getMetadata().getName() + "-" + COMPONENT_APP + "-metrics";
    }

    /**
     * Whether the Prometheus Operator's CRDs are installed on this cluster.
     */
    public static boolean isMonitoringAvailable(KubernetesClient client) {
        if (!detectionDone) {
            synchronized (PrometheusRuleManager.class) {
                if (!detectionDone) {
                    try {
                        var groups = client.getApiGroups();
                        monitoringDetected.set(groups != null && groups.getGroups().stream()
                                .anyMatch(g -> MONITORING_API_GROUP.equals(g.getName())));
                    } catch (Exception ex) {
                        log.warn("Failed to detect the Prometheus Operator API, assuming it is absent", ex);
                        monitoringDetected.set(false);
                    }
                    detectionDone = true;
                    log.info("Prometheus Operator API detected: {}", monitoringDetected.get());
                }
            }
        }
        return monitoringDetected.get();
    }

    public static boolean isEnabled(ApicurioRegistry3 primary) {
        return metricsSpec(primary).map(MetricsSpec::getPrometheusRuleEnabled).orElse(false);
    }

    public static void reconcilePrometheusRule(KubernetesClient client, ApicurioRegistry3 primary) {
        if (!isMonitoringAvailable(client) || !isEnabled(primary)) {
            deletePrometheusRule(client, primary);
            return;
        }

        var name = getRuleName(primary);
        var namespace = primary.getMetadata().getNamespace();

        var desired = new GenericKubernetesResourceBuilder()
                .withApiVersion(MONITORING_API_GROUP + "/" + MONITORING_API_VERSION)
                .withKind(PROMETHEUS_RULE_KIND)
                .withNewMetadata()
                .withName(name)
                .withNamespace(namespace)
                .withLabels(Labels.getSelectorLabels(primary, COMPONENT_APP))
                .endMetadata()
                .build();

        desired.setAdditionalProperties(Map.of("spec", Map.of("groups", List.of(
                Map.of("name", "apicurio-registry-" + primary.getMetadata().getName(),
                        "rules", buildRules(primary, namespace))))));

        try {
            var context = ruleContext();
            var existing = client.genericKubernetesResources(context).inNamespace(namespace).withName(name)
                    .get();
            if (existing == null) {
                client.genericKubernetesResources(context).inNamespace(namespace).resource(desired).create();
                log.info("Created PrometheusRule: {}", name);
            } else {
                desired.getMetadata().setResourceVersion(existing.getMetadata().getResourceVersion());
                client.genericKubernetesResources(context).inNamespace(namespace).resource(desired).update();
                log.debug("Updated PrometheusRule: {}", name);
            }
        } catch (Exception ex) {
            log.warn("Failed to reconcile PrometheusRule {}", name, ex);
        }
    }

    public static void deletePrometheusRule(KubernetesClient client, ApicurioRegistry3 primary) {
        if (!isMonitoringAvailable(client)) {
            return;
        }
        var name = getRuleName(primary);
        var namespace = primary.getMetadata().getNamespace();
        try {
            var context = ruleContext();
            var existing = client.genericKubernetesResources(context).inNamespace(namespace).withName(name)
                    .get();
            if (existing != null) {
                client.genericKubernetesResources(context).inNamespace(namespace).withName(name).delete();
                log.info("Deleted PrometheusRule: {}", name);
            }
        } catch (Exception ex) {
            log.warn("Failed to delete PrometheusRule {}", name, ex);
        }
    }

    /**
     * The alert names and thresholds deliberately mirror the Events the operator emits, so that whichever
     * path an admin is watching tells the same story.
     */
    static List<Map<String, Object>> buildRules(ApicurioRegistry3 primary, String namespace) {
        var spec = metricsSpec(primary);
        double poolThreshold = spec.map(MetricsSpec::getConnectionPoolUtilizationThreshold)
                .orElse(DEFAULT_POOL_UTILIZATION_THRESHOLD);
        double errorThreshold = spec.map(MetricsSpec::getErrorRateThreshold)
                .orElse(DEFAULT_ERROR_RATE_THRESHOLD);
        long lagThreshold = spec.map(MetricsSpec::getKafkaConsumerLagThreshold)
                .orElse(DEFAULT_KAFKA_CONSUMER_LAG_THRESHOLD);
        var selector = "namespace=\"%s\"".formatted(namespace);

        return List.of(
                rule("ApicurioRegistryConnectionPoolSaturated",
                        """
                        agroal_active_count{%s} / clamp_min(agroal_active_count{%s} + agroal_available_count{%s}, 1) >= %s"""
                                .formatted(selector, selector, selector, poolThreshold),
                        "Database connection pool utilization is at or above %s%%."
                                .formatted(poolThreshold * 100)),
                rule("ApicurioRegistryHighErrorRate",
                        """
                        sum(rate(rest_requests_seconds_count{%s,status_code_group=~"5.*"}[5m])) / clamp_min(sum(rate(rest_requests_seconds_count{%s}[5m])), 0.001) >= %s"""
                                .formatted(selector, selector, errorThreshold),
                        "More than %s%% of REST API requests are failing with a 5xx status."
                                .formatted(errorThreshold * 100)),
                rule("ApicurioRegistryKafkaConsumerLagHigh",
                        "kafka_consumer_fetch_manager_records_lag_max{%s} >= %d"
                                .formatted(selector, lagThreshold),
                        "KafkaSQL consumer lag is at or above %d records.".formatted(lagThreshold)));
    }

    private static Map<String, Object> rule(String alert, String expr, String description) {
        return Map.of(
                "alert", alert,
                "expr", expr,
                "for", "5m",
                "labels", Map.of("severity", "warning"),
                "annotations", Map.of("description", description));
    }

    private static CustomResourceDefinitionContext ruleContext() {
        return new CustomResourceDefinitionContext.Builder()
                .withGroup(MONITORING_API_GROUP)
                .withVersion(MONITORING_API_VERSION)
                .withPlural(PROMETHEUS_RULE_PLURAL)
                .withScope("Namespaced")
                .build();
    }

    private static java.util.Optional<MetricsSpec> metricsSpec(ApicurioRegistry3 primary) {
        return ofNullable(primary)
                .map(ApicurioRegistry3::getSpec)
                .map(ApicurioRegistry3Spec::getApp)
                .map(AppSpec::getMetrics);
    }
}
