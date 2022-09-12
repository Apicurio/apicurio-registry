package io.apicurio.registry.systemtests.registryinfra;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.apicurio.registry.systemtests.framework.Constants;
import io.apicurio.registry.systemtests.framework.Environment;
import io.apicurio.registry.systemtests.framework.LoggerUtils;
import io.apicurio.registry.systemtests.platform.Kubernetes;
import io.apicurio.registry.systemtests.registryinfra.resources.ApicurioRegistryResourceType;
import io.apicurio.registry.systemtests.registryinfra.resources.CatalogSourceResourceType;
import io.apicurio.registry.systemtests.registryinfra.resources.DeploymentResourceType;
import io.apicurio.registry.systemtests.registryinfra.resources.IngressResourceType;
import io.apicurio.registry.systemtests.registryinfra.resources.KafkaConnectResourceType;
import io.apicurio.registry.systemtests.registryinfra.resources.KafkaResourceType;
import io.apicurio.registry.systemtests.registryinfra.resources.KafkaTopicResourceType;
import io.apicurio.registry.systemtests.registryinfra.resources.KafkaUserResourceType;
import io.apicurio.registry.systemtests.registryinfra.resources.NamespaceResourceType;
import io.apicurio.registry.systemtests.registryinfra.resources.OperatorGroupResourceType;
import io.apicurio.registry.systemtests.registryinfra.resources.PersistentVolumeClaimResourceType;
import io.apicurio.registry.systemtests.registryinfra.resources.ResourceType;
import io.apicurio.registry.systemtests.registryinfra.resources.RouteResourceType;
import io.apicurio.registry.systemtests.registryinfra.resources.SecretResourceType;
import io.apicurio.registry.systemtests.registryinfra.resources.ServiceResourceType;
import io.apicurio.registry.systemtests.registryinfra.resources.SubscriptionResourceType;
import io.apicurio.registry.systemtests.time.TimeoutBudget;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.strimzi.api.kafka.model.Kafka;
import org.junit.jupiter.api.Assertions;
import org.slf4j.Logger;

import java.text.MessageFormat;
import java.time.Duration;
import java.util.Stack;
import java.util.function.Predicate;

public class ResourceManager {
    private static final Logger LOGGER = LoggerUtils.getLogger();
    private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory());
    private static ResourceManager instance;
    private static final Stack<Runnable> STORED_RESOURCES = new Stack<>();
    private static final Stack<Runnable> SHARED_RESOURCES = new Stack<>();

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
            new SubscriptionResourceType(),
            new CatalogSourceResourceType(),
            new IngressResourceType()
    };

    public <T extends HasMetadata> ResourceType<T> findResourceType(T resource) {
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
            boolean waitReady, T resource
    ) throws InterruptedException {
        String kind = resource.getKind();
        String name = resource.getMetadata().getName();
        String namespace = resource.getMetadata().getNamespace();
        String resourceInfo = MessageFormat.format("{0} with name {1} in namespace {2}", kind, name, namespace);

        LOGGER.info("Creating resource {}...", resourceInfo);

        synchronized (this) {
            if (namespace != null && Kubernetes.getNamespace(namespace) == null) {
                createSharedResource(waitReady, NamespaceResourceType.getDefault(namespace));
            }
        }

        ResourceType<T> type = findResourceType(resource);
        type.createOrReplace(resource);

        synchronized (this) {
            if (!name.equals(Constants.KAFKA)) {
                STORED_RESOURCES.push(() -> deleteResource(resource));
            }
        }

        LOGGER.info("Resource {} created.", resourceInfo);
        if (!name.equals(Constants.KAFKA)) {
            Thread.sleep(Duration.ofMinutes(1).toMillis());
        }
        if (waitReady) {
            Assertions.assertTrue(
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

    public final <T extends HasMetadata> void createSharedResource(
            boolean waitReady, T resource
    ) throws InterruptedException {
        String kind = resource.getKind();
        String name = resource.getMetadata().getName();
        String namespace = resource.getMetadata().getNamespace();
        String resourceInfo = MessageFormat.format("{0} with name {1} in namespace {2}", kind, name, namespace);

        LOGGER.info("Creating shared resource {}...", resourceInfo);

        synchronized (this) {
            if (namespace != null && Kubernetes.getNamespace(namespace) == null) {
                createSharedResource(waitReady, NamespaceResourceType.getDefault(namespace));
            }
        }

        ResourceType<T> type = findResourceType(resource);

        type.createOrReplace(resource);

        synchronized (this) {
            SHARED_RESOURCES.push(() -> deleteResource(resource));
        }

        LOGGER.info("Shared resource {} created.", resourceInfo);

        if (waitReady) {
            Assertions.assertTrue(
                    waitResourceCondition(resource, type::isReady),
                    MessageFormat.format("Timed out waiting for shared resource {0} to be ready.", resourceInfo)
            );

            LOGGER.info("Shared resource {} is ready.", resourceInfo);

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
        Assertions.assertNotNull(resource);
        Assertions.assertNotNull(resource.getMetadata());
        Assertions.assertNotNull(resource.getMetadata().getName());
        ResourceType<T> type = findResourceType(resource);
        Assertions.assertNotNull(type);

        LOGGER.info("Waiting for resource {} to meet the condition...", resource.getKind());

        T res;

        while (!timeout.timeoutExpired()) {
            res = type.get(resource.getMetadata().getNamespace(), resource.getMetadata().getName());

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

        res = type.get(resource.getMetadata().getNamespace(), resource.getMetadata().getName());

        if (!condition.test(res)) {
            LOGGER.error("Resource failed condition check: {}", resourceToString(res));

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
            LOGGER.error("Failed converting resource to YAML: {}", e.getMessage());

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
        /*if (resourceInfo.contains("Subscription") && (resourceInfo.contains("sso") || resourceInfo.contains("keycloak"))) {
            KeycloakUtils.removeKeycloak(resource.getMetadata().getNamespace());
        }*/

        try {
            type.delete(resource);
        } catch (Exception e) {
            e.printStackTrace();
        }

        Assertions.assertTrue(
                waitResourceCondition(resource, type::doesNotExist, TimeoutBudget.ofDuration(Duration.ofMinutes(10))),
                MessageFormat.format("Timed out waiting for resource {0} to be deleted.", resourceInfo)
        );

        LOGGER.info("Resource {} is deleted.", resourceInfo);
    }

    public void deleteKafka() {
        Kafka kafka = KafkaResourceType.getOperation().inNamespace(Environment.NAMESPACE).withName(Constants.KAFKA).get();
        if (kafka != null) {
            deleteResource(kafka);
        }
    }
    public void deleteSharedResources() {
        LOGGER.info("----------------------------------------------");
        LOGGER.info("Going to clear shared resources.");
        LOGGER.info("----------------------------------------------");

        while (!SHARED_RESOURCES.isEmpty()) {
            SHARED_RESOURCES.pop().run();
        }

        LOGGER.info("----------------------------------------------");
        LOGGER.info("");
    }
    public void deleteResources() {
        LOGGER.info("----------------------------------------------");
        LOGGER.info("Going to clear test resources.");
        LOGGER.info("----------------------------------------------");

        while (!STORED_RESOURCES.isEmpty()) {
            STORED_RESOURCES.pop().run();
        }

        LOGGER.info("----------------------------------------------");
        LOGGER.info("");
    }
}
