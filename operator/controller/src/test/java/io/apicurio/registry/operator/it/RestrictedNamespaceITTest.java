package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.ResourceFactory;
import io.apicurio.registry.utils.Cell;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static io.apicurio.registry.operator.Tags.FEATURE;
import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_APP;
import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_UI;
import static io.apicurio.registry.operator.utils.K8sCell.k8sCell;
import static io.apicurio.registry.operator.utils.Mapper.copy;
import static io.apicurio.registry.utils.Cell.cell;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
@Tag(FEATURE)
public class RestrictedNamespaceITTest extends ITBase {

    private static final Logger log = LoggerFactory.getLogger(RestrictedNamespaceITTest.class);

    private static ApicurioRegistry3 createRegistry(String namespace) {
        var registry = ResourceFactory.deserialize("/k8s/examples/simple.apicurioregistry3.yaml", ApicurioRegistry3.class);
        registry.getMetadata().setNamespace(namespace);
        registry.getSpec().getApp().getIngress().setHost(ingressManager.getIngressHost(namespace, "app"));
        registry.getSpec().getUi().getIngress().setHost(ingressManager.getIngressHost(namespace, "ui"));
        client.resource(registry).create();
        return registry;
    }

    @Test
    void smoke() {
        // We will create 3 namespaces, and specify that the operator should watch 2 of them.
        // We need to set the env. variable of the operator deployment, which we can't do with OLM or local deployment,
        // unless we pass the variable in another way. TODO ConfigMap?
        if (operatorDeployment == OperatorDeployment.local) {
            log.warn("This test requires an ability to edit the operator Deployment, so it's not supported when running locally.");
            // TODO: For OLM Deployment, try ConfigMap or annotations.
            return;
        }

        var operatorDeployment = k8sCell(client, ITBase::getOperatorDeployment);

        var namespace1 = calculateNamespace();
        var namespace2 = calculateNamespace();
        var namespace3 = calculateNamespace();

        Cell<EnvVar> originalEnvVar = cell();

        try {

            createNamespace(client, namespace1);
            createNamespace(client, namespace2);
            createNamespace(client, namespace3);

            operatorDeployment.update(r -> {
                var e = getWatchedNamespacesEnvVar(r.getSpec().getTemplate().getSpec());
                // Save the original
                originalEnvVar.set(copy(e));
                e.setValue(namespace1 + "," + namespace2);
                e.setValueFrom(null);
            });

            await().atMost(LONG_DURATION).ignoreExceptions().untilAsserted(() -> {
                // We need to wait until the previous operator pod is completely gone,
                // otherwise it can still reconcile in all namespaces.
                // Therefore, we need to have a strong condition here and awaiting on e.g. deployment replicas is not sufficient.
                assertThat(getWatchedNamespacesEnvVar(waitOnOperatorPodReady().getSpec()).getValue()).isEqualTo(namespace1 + "," + namespace2);
            });
            startOperatorPodLog();

            var registry1 = createRegistry(namespace1);
            var registry2 = createRegistry(namespace2);
            var registry3 = createRegistry(namespace3);

            checkDeploymentExists(registry1, COMPONENT_APP, 1);
            checkDeploymentExists(registry1, COMPONENT_UI, 1);

            checkDeploymentExists(registry2, COMPONENT_APP, 1);
            checkDeploymentExists(registry2, COMPONENT_UI, 1);

            checkDeploymentDoesNotExist(registry3, COMPONENT_APP);
            checkDeploymentDoesNotExist(registry3, COMPONENT_UI);

            // Restore the original env. var
            operatorDeployment.update(r -> {
                var e = getWatchedNamespacesEnvVar(r.getSpec().getTemplate().getSpec());
                e.setValue(originalEnvVar.get().getValue());
                e.setValueFrom(originalEnvVar.get().getValueFrom());
            });

            await().atMost(LONG_DURATION).ignoreExceptions().untilAsserted(() -> {
                // We need to wait until the previous operator pod is completely gone,
                // otherwise it can still reconcile in all namespaces.
                // Therefore, we need to have a strong condition here and awaiting on e.g. deployment replicas is not sufficient.
                assertThat(getWatchedNamespacesEnvVar(waitOnOperatorPodReady().getSpec()).getValue()).isEqualTo(originalEnvVar.get().getValue());
            });
            originalEnvVar.set(null);
            startOperatorPodLog();

            checkDeploymentExists(registry1, COMPONENT_APP, 1);
            checkDeploymentExists(registry1, COMPONENT_UI, 1);

            checkDeploymentExists(registry2, COMPONENT_APP, 1);
            checkDeploymentExists(registry2, COMPONENT_UI, 1);

            checkDeploymentExists(registry3, COMPONENT_APP, 1);
            checkDeploymentExists(registry3, COMPONENT_UI, 1);

        } finally {
            // Let's try to make sure that the original env. var is restored,
            // otherwise it will mess up other tests.
            // Should we do this always or only if cleanup is enabled?
            if (originalEnvVar.isSet()) {
                operatorDeployment.update(r -> {
                    var e = getWatchedNamespacesEnvVar(r.getSpec().getTemplate().getSpec());
                    e.setValue(originalEnvVar.get().getValue());
                    e.setValueFrom(originalEnvVar.get().getValueFrom());
                });
            }
            var operatorPod = waitOnOperatorPodReady();
            var operatorPodId = ResourceID.fromResource(operatorPod);
            if (!podLogManager.isActive(operatorPodId)) {
                startOperatorPodLog();
            }
            if (cleanup) {
                var namespaces = List.of(namespace1, namespace2, namespace3);
                namespaces.forEach(n -> {
                    log.info("Deleting namespace: {}", n);
                    client.namespaces().withName(n).delete();
                });
                // We need to wait until the finalizers run before undeploying the operator
                await().atMost(MEDIUM_DURATION).ignoreExceptions().untilAsserted(() -> {
                    namespaces.forEach(n -> assertThat(client.namespaces().withName(n).get()).isNull());
                });
            }
        }
    }

    private static EnvVar getWatchedNamespacesEnvVar(PodSpec ps) {
        var list = ps.getContainers().stream()
                .filter(c -> "apicurio-registry-operator".equals(c.getName()))
                .flatMap(c -> c.getEnv().stream())
                .filter(e -> "APICURIO_OPERATOR_WATCHED_NAMESPACES".equals(e.getName()))
                .toList();
        assertThat(list).hasSize(1);
        return list.get(0);
    }
}
