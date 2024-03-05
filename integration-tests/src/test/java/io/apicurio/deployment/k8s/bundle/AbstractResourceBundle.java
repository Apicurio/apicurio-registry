package io.apicurio.deployment.k8s.bundle;

import io.apicurio.registry.utils.IoUtil;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.readiness.Readiness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static io.apicurio.deployment.KubernetesTestResources.TEST_NAMESPACE;
import static io.apicurio.deployment.k8s.K8sClientManager.kubernetesClient;
import static io.apicurio.deployment.manual.Utils.SELF;
import static io.apicurio.deployment.manual.Utils.readResource;

/**
 * This class represents a bundle of related Kubernetes resources that should be managed together.
 */
public abstract class AbstractResourceBundle {

    private static final Logger log = LoggerFactory.getLogger(AbstractResourceBundle.class);

    private String resourcesPath;

    private Map<String, String> placeholders;

    private List<ResourceDescriptor> descriptors;

    private boolean initialized;


    protected AbstractResourceBundle() {
    }


    protected void init(String resourcesPath, Map<String, String> placeholders, List<ResourceDescriptor> descriptors) {
        this.resourcesPath = resourcesPath;
        this.placeholders = placeholders;
        this.descriptors = descriptors;
        initialized = true;
    }


    protected void deploy() {
        var resources = readResource(resourcesPath);
        for (Entry<String, String> entry : placeholders.entrySet()) {
            if (entry.getValue() != null) {
                resources = resources.replace(entry.getKey(), entry.getValue());
            }
        }
        kubernetesClient().load(IoUtil.toStream(resources))
                .inNamespace(TEST_NAMESPACE)
                .create();
    }


    public boolean isDeployed() {
        return initialized && descriptors
                .stream()
                .map(d -> kubernetesClient().resources(d.getType()).inNamespace(TEST_NAMESPACE).withLabels(d.getLabels()).list().getItems().size() == d.getCount())
                .allMatch(SELF);
    }


    public void waitUtilDeployed() {
        if (!initialized) {
            throw new IllegalStateException("Not initialized.");
        }
        Awaitility.await().atMost(60, TimeUnit.SECONDS).until(this::isDeployed);
    }


    public boolean isNotDeployed() {
        return !initialized || descriptors
                .stream()
                .map(d -> kubernetesClient().resources(d.getType()).inNamespace(TEST_NAMESPACE).withLabels(d.getLabels()).list().getItems().size() == 0)
                .allMatch(SELF);
    }


    public boolean isReady() {
        return isDeployed() && descriptors
                .stream()
                .flatMap(d -> kubernetesClient().resources(d.getType()).inNamespace(TEST_NAMESPACE).withLabels(d.getLabels()).list().getItems().stream())
                .map(r -> Readiness.getInstance().isReady(r))
                .allMatch(SELF);
    }


    public void waitUtilReady() {
        waitUtilDeployed();
        descriptors.forEach(d -> kubernetesClient()
                .resources(d.getType())
                .inNamespace(TEST_NAMESPACE)
                .withLabels(d.getLabels())
                .waitUntilCondition(r -> Readiness.getInstance().isReady(r), 60, TimeUnit.SECONDS)
        );
    }


    public void delete() {
        if (!initialized) {
            return;
        }
        descriptors.forEach(d -> {
            try {
                kubernetesClient().resources(d.getType()).inNamespace(TEST_NAMESPACE).withLabels(d.getLabels()).delete();
            } catch (KubernetesClientException ex) {
                log.debug("Ignoring: {}", ex.getMessage());
            }
        });
    }


    public void deleteAndWait() {
        if (!initialized) {
            return;
        }
        delete();
        descriptors.forEach(d -> kubernetesClient().resources(d.getType()).inNamespace(TEST_NAMESPACE).withLabels(d.getLabels()).waitUntilCondition(Objects::isNull, 60, TimeUnit.SECONDS));
    }
}
