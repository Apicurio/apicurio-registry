package io.apicurio.registry.operator.utils;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;

import static io.apicurio.registry.operator.utils.Mapper.toYAML;

/**
 * Dumps Kubernetes cluster state to the log on test failure. Called automatically by
 * {@link OperatorTestExtension} when a test fails.
 *
 * <p>
 * Dumps full YAML for: ApicurioRegistry3 CRs, Deployments, Services, Ingresses, Routes, and
 * NetworkPolicies. Dumps status only for Pods (phase, conditions, container statuses) and Events (last 50,
 * sorted by timestamp). For OLM tests, also dumps Subscriptions, InstallPlans, CSVs (v0) and
 * ClusterExtensions, ClusterCatalogs (v1).
 *
 * <p>
 * Each resource type is dumped in its own try-catch, so a failure to list one type (e.g. Routes on
 * non-OpenShift clusters) does not prevent other diagnostics from being collected.
 */
public final class ClusterDiagnostics {

    private static final Logger log = LoggerFactory.getLogger(ClusterDiagnostics.class);

    private static final int MAX_EVENTS = 50;
    private static final int MAX_LOG_LINES = 100;

    private ClusterDiagnostics() {
    }

    public static void dump(KubernetesClient client, String namespace, boolean isOLM) {
        log.error("==================== CLUSTER DIAGNOSTICS ====================");
        log.error("Namespace: {}", namespace);

        dumpApicurioRegistryCRs(client, namespace);
        dumpDeployments(client, namespace);
        dumpServices(client, namespace);
        dumpIngresses(client, namespace);
        dumpRoutes(client, namespace);
        dumpNetworkPolicies(client, namespace);
        dumpPods(client, namespace);
        dumpEvents(client, namespace);

        if (isOLM) {
            dumpOLMResources(client, namespace);
        }

        log.error("================ END CLUSTER DIAGNOSTICS =================");
    }

    private static void dumpApicurioRegistryCRs(KubernetesClient client, String namespace) {
        log.error("--- ApicurioRegistry3 CRs ---");
        try {
            var crs = client.resources(ApicurioRegistry3.class).inNamespace(namespace).list().getItems();
            if (crs.isEmpty()) {
                log.error("  (none)");
                return;
            }
            for (var cr : crs) {
                log.error("  {}:\n{}", cr.getMetadata().getName(), toYAML(cr));
            }
        } catch (Exception e) {
            log.error("  Failed to list ApicurioRegistry3 CRs: {}", e.getMessage());
        }
    }

    private static void dumpDeployments(KubernetesClient client, String namespace) {
        log.error("--- Deployments ---");
        try {
            var deployments = client.apps().deployments().inNamespace(namespace).list().getItems();
            if (deployments.isEmpty()) {
                log.error("  (none)");
                return;
            }
            for (var d : deployments) {
                log.error("  {}:\n{}", d.getMetadata().getName(), toYAML(d));
            }
        } catch (Exception e) {
            log.error("  Failed to list Deployments: {}", e.getMessage());
        }
    }

    private static void dumpServices(KubernetesClient client, String namespace) {
        log.error("--- Services ---");
        try {
            var services = client.services().inNamespace(namespace).list().getItems();
            if (services.isEmpty()) {
                log.error("  (none)");
                return;
            }
            for (var s : services) {
                log.error("  {}:\n{}", s.getMetadata().getName(), toYAML(s));
            }
        } catch (Exception e) {
            log.error("  Failed to list Services: {}", e.getMessage());
        }
    }

    private static void dumpIngresses(KubernetesClient client, String namespace) {
        log.error("--- Ingresses ---");
        try {
            var ingresses = client.network().v1().ingresses().inNamespace(namespace).list().getItems();
            if (ingresses.isEmpty()) {
                log.error("  (none)");
                return;
            }
            for (var i : ingresses) {
                log.error("  {}:\n{}", i.getMetadata().getName(), toYAML(i));
            }
        } catch (Exception e) {
            log.error("  Failed to list Ingresses: {}", e.getMessage());
        }
    }

    private static void dumpRoutes(KubernetesClient client, String namespace) {
        log.error("--- Routes ---");
        try {
            var routes = client.genericKubernetesResources("route.openshift.io/v1", "Route")
                    .inNamespace(namespace).list().getItems();
            if (routes.isEmpty()) {
                log.error("  (none)");
                return;
            }
            for (var r : routes) {
                log.error("  {}:\n{}", r.getMetadata().getName(), toYAML(r));
            }
        } catch (Exception e) {
            log.error("  Routes not available: {}", e.getMessage());
        }
    }

    private static void dumpNetworkPolicies(KubernetesClient client, String namespace) {
        log.error("--- NetworkPolicies ---");
        try {
            var policies = client.network().v1().networkPolicies().inNamespace(namespace).list().getItems();
            if (policies.isEmpty()) {
                log.error("  (none)");
                return;
            }
            for (var p : policies) {
                log.error("  {}:\n{}", p.getMetadata().getName(), toYAML(p));
            }
        } catch (Exception e) {
            log.error("  Failed to list NetworkPolicies: {}", e.getMessage());
        }
    }

    private static void dumpPods(KubernetesClient client, String namespace) {
        log.error("--- Pods (status) ---");
        try {
            var pods = client.pods().inNamespace(namespace).list().getItems();
            if (pods.isEmpty()) {
                log.error("  (none)");
                return;
            }
            for (var pod : pods) {
                log.error("  Pod: {} | Phase: {}", pod.getMetadata().getName(),
                        pod.getStatus().getPhase());
                dumpPodStatus(pod);
                dumpContainerLogs(client, pod);
            }
        } catch (Exception e) {
            log.error("  Failed to list Pods: {}", e.getMessage());
        }
    }

    private static void dumpPodStatus(Pod pod) {
        var status = pod.getStatus();
        if (status.getConditions() != null && !status.getConditions().isEmpty()) {
            log.error("    Conditions:\n{}", toYAML(status.getConditions()));
        }
        if (status.getContainerStatuses() != null) {
            for (var cs : status.getContainerStatuses()) {
                log.error("    Container {}: ready={}, restarts={}, state={}",
                        cs.getName(), cs.getReady(), cs.getRestartCount(),
                        toYAML(cs.getState()));
            }
        }
    }

    private static void dumpContainerLogs(KubernetesClient client, Pod pod) {
        var namespace = pod.getMetadata().getNamespace();
        var podName = pod.getMetadata().getName();
        if (pod.getSpec().getContainers() == null) {
            return;
        }
        for (var container : pod.getSpec().getContainers()) {
            try {
                var logs = client.pods().inNamespace(namespace).withName(podName)
                        .inContainer(container.getName()).tailingLines(MAX_LOG_LINES).getLog();
                log.error("    --- Container {} logs (last {} lines) ---\n{}",
                        container.getName(), MAX_LOG_LINES, logs);
            } catch (Exception e) {
                log.error("    --- Container {} logs: unavailable ({}) ---",
                        container.getName(), e.getMessage());
            }
        }
    }

    private static void dumpEvents(KubernetesClient client, String namespace) {
        log.error("--- Events (last {}) ---", MAX_EVENTS);
        try {
            var events = client.v1().events().inNamespace(namespace).list().getItems();
            events.sort(Comparator.comparing(
                    (Event e) -> e.getLastTimestamp() != null ? e.getLastTimestamp() : "",
                    Comparator.reverseOrder()));

            if (events.isEmpty()) {
                log.error("  (none)");
                return;
            }

            var limited = events.subList(0, Math.min(events.size(), MAX_EVENTS));
            for (var event : limited) {
                log.error("  {} | {} | {} | {}/{} | {}",
                        event.getLastTimestamp(),
                        event.getType(),
                        event.getReason(),
                        event.getInvolvedObject().getKind(),
                        event.getInvolvedObject().getName(),
                        event.getMessage());
            }
        } catch (Exception e) {
            log.error("  Failed to list Events: {}", e.getMessage());
        }
    }

    private static void dumpOLMResources(KubernetesClient client, String namespace) {
        log.error("--- OLM Resources ---");

        dumpGenericResources(client, "operators.coreos.com/v1alpha1", "Subscription", namespace);
        dumpGenericResources(client, "operators.coreos.com/v1alpha1", "InstallPlan", namespace);
        dumpGenericResources(client, "operators.coreos.com/v1alpha1", "ClusterServiceVersion", namespace);

        dumpGenericClusterResources(client, "olm.operatorframework.io/v1", "ClusterExtension");
        dumpGenericClusterResources(client, "olm.operatorframework.io/v1", "ClusterCatalog");
    }

    private static void dumpGenericResources(KubernetesClient client, String apiVersion, String kind,
            String namespace) {
        try {
            var resources = client.genericKubernetesResources(apiVersion, kind)
                    .inNamespace(namespace).list().getItems();
            if (resources.isEmpty()) {
                log.error("  {} (none)", kind);
                return;
            }
            for (var r : resources) {
                log.error("  {} {}:\n{}", kind, r.getMetadata().getName(), toYAML(r));
            }
        } catch (Exception e) {
            log.error("  {} not available: {}", kind, e.getMessage());
        }
    }

    private static void dumpGenericClusterResources(KubernetesClient client, String apiVersion, String kind) {
        try {
            var resources = client.genericKubernetesResources(apiVersion, kind).list().getItems();
            if (resources.isEmpty()) {
                log.error("  {} (none)", kind);
                return;
            }
            for (var r : resources) {
                log.error("  {} {}:\n{}", kind, r.getMetadata().getName(), toYAML(r));
            }
        } catch (Exception e) {
            log.error("  {} not available: {}", kind, e.getMessage());
        }
    }
}
