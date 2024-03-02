package io.apicurio.deployment.k8s.bundle;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.junit.jupiter.api.Assertions;

import java.util.List;
import java.util.Map;

import static io.apicurio.deployment.KubernetesTestResources.TEST_NAMESPACE;
import static io.apicurio.deployment.TestConfiguration.Constants.REGISTRY_KAFKASQL_IMAGE;
import static io.apicurio.deployment.k8s.K8sClientManager.kubernetesClient;

public class ManualRegistryBundle extends AbstractResourceBundle {

    private Map<String, String> labels;


    public void deploy(int nodeId, String image, List<String> javaOptions, int port) {

        if (image == null) {
            image = System.getProperty(REGISTRY_KAFKASQL_IMAGE);
        }

        labels = Map.of("app", "apicurio-registry-node-" + nodeId);

        init("/infra/kafka/registry-kafka-manual.yml",
                Map.of(
                        "PLACEHOLDER_REGISTRY_NODE_ID", String.valueOf(nodeId),
                        "PLACEHOLDER_REGISTRY_IMAGE", image,
                        "PLACEHOLDER_JAVA_OPTIONS", "'" + String.join(" ", javaOptions) + "'",
                        "PLACEHOLDER_REGISTRY_PORT", String.valueOf(port)
                ),
                List.of(
                        ResourceDescriptor.builder().type(Service.class).count(1).labels(labels).build(),
                        ResourceDescriptor.builder().type(Deployment.class).count(1).labels(labels).build(),
                        ResourceDescriptor.builder().type(Pod.class).count(1).labels(labels).build()
                )
        );

        super.deploy();
    }


    public HasMetadata getPodRef() {
        if (!isDeployed()) {
            throw new IllegalStateException("Not deployed.");
        }
        var pods = kubernetesClient()
                .pods()
                .inNamespace(TEST_NAMESPACE)
                .withLabels(labels)
                .list()
                .getItems();
        Assertions.assertEquals(1, pods.size());
        return pods.get(0);
    }
}
