package io.apicurio.deployment.k8s;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;

public class K8sClientManager {

    private static KubernetesClient client;


    public static KubernetesClient kubernetesClient() {
        if (client == null) {
            client = new KubernetesClientBuilder()
                    .build();
        }
        return client;
    }


    public static void closeKubernetesClient() {
        if (client != null) {
            client.close();
            client = null;
        }
    }
}
