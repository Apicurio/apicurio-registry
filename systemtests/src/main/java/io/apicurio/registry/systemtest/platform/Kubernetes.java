package io.apicurio.registry.systemtest.platform;

import io.apicurio.registry.systemtest.messaginginfra.ResourceManager;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.openshift.client.OpenShiftConfig;

public class Kubernetes {
    private static Kubernetes instance;

    private static Config config;

    private static KubernetesClient client;

    private Kubernetes() {
        config = Config.autoConfigure(System.getenv().getOrDefault("TEST_CLUSTER_CONTEXT", null));
        client = new DefaultOpenShiftClient(new OpenShiftConfig(config));
    }

    public static Kubernetes getInstance() {
        if (instance == null) {
            instance = new Kubernetes();
        }

        return instance;
    }

    public static KubernetesClient getClient() {
        return Kubernetes.getInstance().client;
    }
}
