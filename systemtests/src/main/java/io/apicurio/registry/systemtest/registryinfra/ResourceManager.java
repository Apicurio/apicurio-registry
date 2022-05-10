package io.apicurio.registry.systemtest.registryinfra;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.apicurio.registry.systemtest.framework.LoggerUtils;
import io.apicurio.registry.systemtest.framework.ResourceUtils;
import io.apicurio.registry.systemtest.platform.Kubernetes;
import io.apicurio.registry.systemtest.registryinfra.resources.ApicurioRegistryResourceType;
import io.apicurio.registry.systemtest.registryinfra.resources.DeploymentResourceType;
import io.apicurio.registry.systemtest.registryinfra.resources.KafkaConnectResourceType;
import io.apicurio.registry.systemtest.registryinfra.resources.KafkaResourceType;
import io.apicurio.registry.systemtest.registryinfra.resources.KafkaTopicResourceType;
import io.apicurio.registry.systemtest.registryinfra.resources.KafkaUserResourceType;
import io.apicurio.registry.systemtest.registryinfra.resources.NamespaceResourceType;
import io.apicurio.registry.systemtest.registryinfra.resources.OperatorGroupResourceType;
import io.apicurio.registry.systemtest.registryinfra.resources.PersistentVolumeClaimResourceType;
import io.apicurio.registry.systemtest.registryinfra.resources.ResourceType;
import io.apicurio.registry.systemtest.registryinfra.resources.RouteResourceType;
import io.apicurio.registry.systemtest.registryinfra.resources.SecretResourceType;
import io.apicurio.registry.systemtest.registryinfra.resources.ServiceResourceType;
import io.apicurio.registry.systemtest.registryinfra.resources.SubscriptionResourceType;
import io.apicurio.registry.systemtest.time.TimeoutBudget;
import io.fabric8.kubernetes.api.model.HasMetadata;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;

import java.text.MessageFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Stack;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ResourceManager {
    private static final Logger LOGGER = LoggerUtils.getLogger();
    private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory());
    private static ResourceManager instance;
    private static final Map<String, Stack<Runnable>> STORED_RESOURCES = new LinkedHashMap<>();

    public static synchronized ResourceManager getInstance() {
        if (instance == null) {
            instance = new ResourceManager();
        }

        return instance;
    }

    private final ResourceType<?>[] resourceTypes = new ResourceType[]{
            new ApicurioRegistryResourceType(),
            new NamespaceResourceType(),
            new ServiceResourceType(),
            new DeploymentResourceType(),
            new PersistentVolumeClaimResourceType(),
            new KafkaResourceType(),
            new KafkaTopicResourceType(),
            new KafkaUserResourceType(),
            new KafkaConnectResourceType(),
            new RouteResourceType(),
            new SecretResourceType(),
            new OperatorGroupResourceType(),
            new SubscriptionResourceType()
    };

    private <T extends HasMetadata> ResourceType<T> findResourceType(T resource) {
        ResourceType<T> result = null;

        for (ResourceType<?> type : resourceTypes) {
            if (type.getKind().equals(resource.getKind())) {
                result = (ResourceType<T>) type;
                break;
            }
        }

        return result;
    }

    public final <T extends HasMetadata> void createResource(
            ExtensionContext testContext, boolean waitReady, T resource
    ) {
        String kind = resource.getKind();
        String name = resource.getMetadata().getName();
        String namespace = resource.getMetadata().getNamespace();
        String resourceInfo = MessageFormat.format("{0} with name {1} in namespace {2}", kind, name, namespace);

        LOGGER.info("Creating resource {}...", resourceInfo);

        synchronized (this) {
            if (namespace != null && Kubernetes.getNamespace(namespace) == null) {
                createResource(testContext, waitReady, ResourceUtils.buildNamespace(namespace));
            }
        }

        ResourceType<T> type = findResourceType(resource);

        type.create(resource);

        synchronized (this) {
            STORED_RESOURCES.computeIfAbsent(testContext.getDisplayName(), k -> new Stack<>());
            STORED_RESOURCES.get(testContext.getDisplayName()).push(() -> deleteResource(resource));
        }

        LOGGER.info("Resource {} created.", resourceInfo);

        if(waitReady) {
            assertTrue(
                    waitResourceCondition(resource, type::isReady),
                    MessageFormat.format("Timed out waiting for resource {0} to be ready.", resourceInfo)
            );

            LOGGER.info("Resource {} is ready.", resourceInfo);

            T updated = type.get(resource.getMetadata().getNamespace(), resource.getMetadata().getName());
            type.refreshResource(resource, updated);
        } else {
            LOGGER.info("Do not wait for resource {} to be ready.", resourceInfo);
        }
    }

    public final <T extends HasMetadata> boolean waitResourceCondition(T resource, Predicate<T> condition) {
        return waitResourceCondition(
                resource,
                condition,
                TimeoutBudget.ofDuration(findResourceType(resource).getTimeout())
        );
    }

    public final <T extends HasMetadata> boolean waitResourceCondition(
            T resource, Predicate<T> condition, TimeoutBudget timeout
    ) {
        assertNotNull(resource);
        assertNotNull(resource.getMetadata());
        assertNotNull(resource.getMetadata().getName());
        ResourceType<T> type = findResourceType(resource);
        assertNotNull(type);

        LOGGER.info("Waiting for resource {} to be ready...", resource.getKind());

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

        if (!condition.test(res)) {
            LOGGER.info("Resource failed condition check: {}", resourceToString(res));

            return false;
        }

        return true;
    }

    public static <T extends HasMetadata> String resourceToString(T resource) {
        if (resource == null) {
            return "null";
        }
        try {
            return MAPPER.writeValueAsString(resource);
        } catch (JsonProcessingException e) {
            LOGGER.info("Failed converting resource to YAML: {}", e.getMessage());
            return "unknown";
        }
    }

    public final <T extends HasMetadata> void deleteResource(T resource) {
        String resourceInfo = MessageFormat.format(
                "{0} with name {1} in namespace {2}",
                resource.getKind(), resource.getMetadata().getName(), resource.getMetadata().getNamespace()
        );

        LOGGER.info("Deleting resource {}...", resourceInfo);

        ResourceType<T> type = findResourceType(resource);

        try {
            type.delete(resource);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if(type.get(resource.getMetadata().getNamespace(), resource.getMetadata().getName()) == null) {
            LOGGER.info("Resource {} deleted.", resourceInfo);
        } else {
            LOGGER.warn("Resource {} is not deleted yet.", resourceInfo);
        }
    }

    public void deleteResources(ExtensionContext testContext) {
        LOGGER.info("----------------------------------------------");
        LOGGER.info("Going to clear all resources.");
        LOGGER.info("----------------------------------------------");
        LOGGER.info("Resources key: {}", testContext.getDisplayName());

        if (
                !STORED_RESOURCES.containsKey(testContext.getDisplayName())
                || STORED_RESOURCES.get(testContext.getDisplayName()).isEmpty()
        ) {
            LOGGER.info("Nothing to delete");
        }

        while (!STORED_RESOURCES.get(testContext.getDisplayName()).isEmpty()) {
            STORED_RESOURCES.get(testContext.getDisplayName()).pop().run();
        }

        LOGGER.info("----------------------------------------------");
        LOGGER.info("");
        STORED_RESOURCES.remove(testContext.getDisplayName());
    }
}
