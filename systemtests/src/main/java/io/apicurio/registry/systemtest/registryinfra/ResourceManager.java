package io.apicurio.registry.systemtest.registryinfra;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.apicurio.registry.systemtest.framework.LoggerUtils;
import io.apicurio.registry.systemtest.registryinfra.resources.ApicurioRegistryResourceType;
import io.apicurio.registry.systemtest.registryinfra.resources.NamespaceResourceType;
import io.apicurio.registry.systemtest.registryinfra.resources.ResourceType;
import io.apicurio.registry.systemtest.platform.Kubernetes;
import io.apicurio.registry.systemtest.time.TimeoutBudget;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Stack;
import java.util.stream.Collectors;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ResourceManager {
    private static Logger resourceManagerLogger = LoggerUtils.getLogger();

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

    public final <T extends HasMetadata> void createResource(ExtensionContext testContext, boolean waitReady, T resource) throws Exception {
        resourceManagerLogger.info("Creating resource {} with name {} in namespace {}...", resource.getKind(), resource.getMetadata().getName(), resource.getMetadata().getNamespace());

        ResourceType<T> type = findResourceType(resource);

        synchronized (this) {
            if(resource.getMetadata().getNamespace() != null && !Kubernetes.getClient().namespaces().list().getItems().stream().map(n -> n.getMetadata().getName())
                    .collect(Collectors.toList()).contains((resource.getMetadata().getNamespace()))) {
                createResource(testContext, waitReady, new NamespaceBuilder().editOrNewMetadata().withName(resource.getMetadata().getNamespace()).endMetadata().build());
            }
        }

        type.create(resource);

        synchronized (this) {
            storedResources.computeIfAbsent(testContext.getDisplayName(), k -> new Stack<>());
            storedResources.get(testContext.getDisplayName()).push(() -> deleteResource(resource));
        }

        resourceManagerLogger.info("Resource {} with name {} created in namespace {}.", resource.getKind(), resource.getMetadata().getName(), resource.getMetadata().getNamespace());

        if(waitReady) {
            resourceManagerLogger.info("Waiting for resource {} with name {} to be ready in namespace {}...", resource.getKind(), resource.getMetadata().getName(), resource.getMetadata().getNamespace());

            assertTrue(waitResourceCondition(resource, type::isReady),
                    String.format("Timed out waiting for resource {} with name {} to be ready in namespace {}.", resource.getKind(), resource.getMetadata().getName(), resource.getMetadata().getNamespace()));

            resourceManagerLogger.info("Resource {} with name {} is ready in namespace {}.", resource.getKind(), resource.getMetadata().getName(), resource.getMetadata().getNamespace());

            T updated = type.get(resource.getMetadata().getNamespace(), resource.getMetadata().getName());
            type.refreshResource(resource, updated);
        }
    }

    public final <T extends HasMetadata> boolean waitResourceCondition(T resource, Predicate<T> condition) {
        return waitResourceCondition(resource, condition, TimeoutBudget.ofDuration(Duration.ofMinutes(5)));
    }

    public final <T extends HasMetadata> boolean waitResourceCondition(T resource, Predicate<T> condition, TimeoutBudget timeout) {
        assertNotNull(resource);
        assertNotNull(resource.getMetadata());
        assertNotNull(resource.getMetadata().getName());
        ResourceType<T> type = findResourceType(resource);
        assertNotNull(type);

        while (!timeout.timeoutExpired()) {
            T res = type.get(resource.getMetadata().getNamespace(), resource.getMetadata().getName());
            if (condition.test(res)) {
                return true;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        T res = type.get(resource.getMetadata().getNamespace(), resource.getMetadata().getName());
        boolean pass = condition.test(res);
        if (!pass) {
            resourceManagerLogger.info("Resource failed condition check: {}", resourceToString(res));
        }
        return pass;
    }

    private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    public static <T extends HasMetadata> String resourceToString(T resource) {
        if (resource == null) {
            return "null";
        }
        try {
            return mapper.writeValueAsString(resource);
        } catch (JsonProcessingException e) {
            resourceManagerLogger.info("Failed converting resource to YAML: {}", e.getMessage());
            return "unknown";
        }
    }

    public final <T extends HasMetadata> void deleteResource(T resource) {
        resourceManagerLogger.info("Deleting resource {} with name {} in namespace {}...", resource.getKind(), resource.getMetadata().getName(), resource.getMetadata().getNamespace());

        ResourceType<T> type = findResourceType(resource);

        try {
            type.delete(resource);
        } catch (Exception e) {
            e.printStackTrace();
        }

        resourceManagerLogger.info("Resource {} with name {} deleted in namespace {}.", resource.getKind(), resource.getMetadata().getName(), resource.getMetadata().getNamespace());
    }

    public void deleteResources(ExtensionContext testContext) {
        resourceManagerLogger.info("----------------------------------------------");
        resourceManagerLogger.info("Going to clear all resources.");
        resourceManagerLogger.info("----------------------------------------------");
        resourceManagerLogger.info("Resources key: {}", testContext.getDisplayName());
        if (!storedResources.containsKey(testContext.getDisplayName()) || storedResources.get(testContext.getDisplayName()).isEmpty()) {
            resourceManagerLogger.info("Nothing to delete");
        }
        while (!storedResources.get(testContext.getDisplayName()).isEmpty()) {
            storedResources.get(testContext.getDisplayName()).pop().run();
        }
        resourceManagerLogger.info("----------------------------------------------");
        resourceManagerLogger.info("");
        storedResources.remove(testContext.getDisplayName());
    }
}
