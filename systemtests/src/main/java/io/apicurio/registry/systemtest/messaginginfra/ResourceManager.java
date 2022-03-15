package io.apicurio.registry.systemtest.messaginginfra;

import io.apicurio.registry.systemtest.messaginginfra.resources.ApicurioRegistryResourceType;
import io.apicurio.registry.systemtest.messaginginfra.resources.NamespaceResourceType;
import io.apicurio.registry.systemtest.messaginginfra.resources.ResourceType;
import io.apicurio.registry.systemtest.platform.Kubernetes;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ResourceManager {
    private static Logger LOGGER = LoggerFactory.getLogger(ResourceManager.class);

    private static ResourceManager instance;

    private static Map<String, Stack<Runnable>> storedResources = new LinkedHashMap<>();

    public static synchronized ResourceManager getInstance() {
        if (instance == null) {
            instance = new ResourceManager();
        }

        return instance;
    }

    private final ResourceType<?>[] resourceTypes = new ResourceType[]{
            new ApicurioRegistryResourceType(),
            new NamespaceResourceType()
    };

    private <T extends HasMetadata> ResourceType<T> findResourceType(T resource) {
        for (ResourceType<?> type : resourceTypes) {
            if (type.getKind().equals(resource.getKind())) {
                return (ResourceType<T>) type;
            }
        }
        return null;
    }

    public final <T extends HasMetadata> void createResource(String testName, boolean waitReady, T resource) throws Exception {
        LOGGER.info("Creating resource {}:{}...", resource.getClass(), resource.getMetadata().getName());

        ResourceType<T> type = findResourceType(resource);

        synchronized (this) {
            if(resource.getMetadata().getNamespace() != null && !Kubernetes.getClient().namespaces().list().getItems().stream().map(n -> n.getMetadata().getName())
                    .collect(Collectors.toList()).contains((resource.getMetadata().getNamespace()))) {
                createResource(testName, waitReady, new NamespaceBuilder().editOrNewMetadata().withName(resource.getMetadata().getNamespace()).endMetadata().build());
            }
        }

        type.create(resource);

        synchronized (this) {
            storedResources.computeIfAbsent(testName, k -> new Stack<>());
            storedResources.get(testName).push(() -> {
                try {
                    deleteResource(testName, resource);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        LOGGER.info("Resource {}:{} created.", resource.getClass(), resource.getMetadata().getName());

        if(waitReady) {
            LOGGER.info("Resource {}:{} ready.", resource.getClass(), resource.getMetadata().getName());
        }

        try {
            TimeUnit.SECONDS.sleep(10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public final <T extends HasMetadata> void deleteResource(String testName, T resource) throws Exception {
        LOGGER.info("Deleting resource {}:{}...", resource.getClass(), resource.getMetadata().getName());

        ResourceType<T> type = findResourceType(resource);

        type.delete(resource);

        LOGGER.info("Resource {}:{} deleted.", resource.getClass(), resource.getMetadata().getName());

        try {
            TimeUnit.SECONDS.sleep(10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void deleteResources(String testName) {
        LOGGER.info("----------------------------------------------");
        LOGGER.info("Going to clear all resources.");
        LOGGER.info("----------------------------------------------");
        LOGGER.info("Resource: {}", testName);
        if (!storedResources.containsKey(testName) || storedResources.get(testName).isEmpty()) {
            LOGGER.info("Nothing to delete");
        }
        while (!storedResources.get(testName).isEmpty()) {
            storedResources.get(testName).pop().run();
        }
        LOGGER.info("----------------------------------------------");
        LOGGER.info("");
        storedResources.remove(testName);
    }
}
