package io.apicurio.registry.systemtest;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.openshift.client.OpenShiftConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Stack;
import java.util.function.UnaryOperator;

public class ResourceManager {
    private static Logger LOGGER = LoggerFactory.getLogger(ResourceManager.class);

    private static ResourceManager instance = new ResourceManager();

    private static Map<String, Stack<Runnable>> storedResources = new LinkedHashMap<>();

    private Config config;

    private OpenShiftClient openShiftClient;

    private ResourceManager() {
        config = Config.autoConfigure(System.getenv().getOrDefault("TEST_CLUSTER_CONTEXT", null));
        openShiftClient = new DefaultOpenShiftClient(new OpenShiftConfig(config));

        // Namespace n = openShiftClient.namespaces().withName("default").get();

        // LOGGER.info(n.toString());
    }

    public <T extends HasMetadata> MixedOperation<T, KubernetesResourceList<T>, Resource<T>> getOperation(Class t) {
        return openShiftClient.resources(t);
    }

    public static ResourceManager getInstance() {
        return instance;
    }

    public <T extends HasMetadata> T create(T resource) {
        getOperation(resource.getClass()).create(resource);

        return resource;
    }

    public <T extends HasMetadata> T edit(T resource, UnaryOperator<T> function) {
        // TODO
        return resource;
    }

    public <T extends HasMetadata> void delete(T resource) {
        // TODO
        getOperation(resource.getClass()).delete(resource);
    }
}
