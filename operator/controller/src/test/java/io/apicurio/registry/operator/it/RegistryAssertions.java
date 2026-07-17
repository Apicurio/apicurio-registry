package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.utils.Cell;
import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicy;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import io.fabric8.kubernetes.client.KubernetesClient;

import static io.apicurio.registry.operator.it.ITBase.MEDIUM_DURATION;
import static io.apicurio.registry.operator.it.ITBase.SHORT_DURATION;
import static io.apicurio.registry.utils.Cell.cell;
import static java.time.Duration.ofSeconds;
import static java.util.Optional.ofNullable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class RegistryAssertions {

    private final KubernetesClient client;
    private final String namespace;

    public RegistryAssertions(KubernetesClient client, String namespace) {
        this.client = client;
        this.namespace = namespace;
    }

    private String namespaceOf(ApicurioRegistry3 primary) {
        return ofNullable(primary.getMetadata().getNamespace()).orElse(namespace);
    }

    private String resourceName(ApicurioRegistry3 primary, String component, String suffix) {
        return primary.getMetadata().getName() + "-" + component + "-" + suffix;
    }

    public void checkDeploymentExists(ApicurioRegistry3 primary, String component, int replicas) {
        await().atMost(MEDIUM_DURATION).ignoreExceptions().untilAsserted(() -> {
            assertThat(client.apps().deployments().inNamespace(namespaceOf(primary))
                    .withName(resourceName(primary, component, "deployment")).get()
                    .getStatus().getReadyReplicas()).isEqualTo(replicas);
        });
    }

    public void checkDeploymentDoesNotExist(ApicurioRegistry3 primary, String component) {
        Runnable check = () -> {
            assertThat(client.apps().deployments().inNamespace(namespaceOf(primary))
                    .withName(resourceName(primary, component, "deployment")).get()).isNull();
        };
        await().during(ofSeconds(10)).atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(check::run);
        check.run();
    }

    public void checkServiceExists(ApicurioRegistry3 primary, String component) {
        await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
            assertThat(client.services().inNamespace(namespaceOf(primary))
                    .withName(resourceName(primary, component, "service")).get()).isNotNull();
        });
    }

    public void checkServiceDoesNotExist(ApicurioRegistry3 primary, String component) {
        Runnable check = () -> {
            assertThat(client.services().inNamespace(namespaceOf(primary))
                    .withName(resourceName(primary, component, "service")).get()).isNull();
        };
        await().during(ofSeconds(10)).atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(check::run);
        check.run();
    }

    public void checkIngressExists(ApicurioRegistry3 primary, String component) {
        await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
            assertThat(client.network().v1().ingresses().inNamespace(namespaceOf(primary))
                    .withName(resourceName(primary, component, "ingress")).get()).isNotNull();
        });
    }

    public void checkIngressDoesNotExist(ApicurioRegistry3 primary, String component) {
        Runnable check = () -> {
            assertThat(client.network().v1().ingresses().inNamespace(namespaceOf(primary))
                    .withName(resourceName(primary, component, "ingress")).get()).isNull();
        };
        await().during(ofSeconds(10)).atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(check::run);
        check.run();
    }

    public PodDisruptionBudget checkPodDisruptionBudgetExists(ApicurioRegistry3 primary, String component) {
        final Cell<PodDisruptionBudget> rval = cell();
        await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
            PodDisruptionBudget pdb = client.policy().v1().podDisruptionBudget()
                    .inNamespace(namespaceOf(primary))
                    .withName(resourceName(primary, component, "poddisruptionbudget")).get();
            assertThat(pdb).isNotNull();
            rval.set(pdb);
        });
        return rval.get();
    }

    public NetworkPolicy checkNetworkPolicyExists(ApicurioRegistry3 primary, String component) {
        final Cell<NetworkPolicy> rval = cell();
        await().atMost(SHORT_DURATION).ignoreExceptions().untilAsserted(() -> {
            NetworkPolicy networkPolicy = client.network().v1().networkPolicies()
                    .inNamespace(namespaceOf(primary))
                    .withName(resourceName(primary, component, "networkpolicy")).get();
            assertThat(networkPolicy).isNotNull();
            rval.set(networkPolicy);
        });
        return rval.get();
    }
}
