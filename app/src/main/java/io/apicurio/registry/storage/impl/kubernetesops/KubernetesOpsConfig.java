package io.apicurio.registry.storage.impl.kubernetesops;

import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.storage.impl.polling.AbstractPollingStorageConfig;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.Optional;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_KUBERNETESOPS;

@ApplicationScoped
public class KubernetesOpsConfig extends AbstractPollingStorageConfig {

    @Inject
    KubernetesClient kubernetesClient;

    @ConfigProperty(name = "apicurio.kubernetesops.namespace")
    @Info(category = CATEGORY_KUBERNETESOPS, experimental = true, description = """
            Kubernetes namespace to watch for ConfigMaps. \
            If not specified, auto-detects the pod's namespace or falls back to "default".""", availableSince = "3.0.0")
    @Getter
    Optional<String> namespace;

    @ConfigProperty(name = "apicurio.kubernetesops.label.registry-id", defaultValue = "apicurio.io/registry-id")
    @Info(category = CATEGORY_KUBERNETESOPS, experimental = true, description = """
            Label key used to identify ConfigMaps belonging to this registry instance. \
            The label value must match `apicurio.polling-storage.id`.""", availableSince = "3.0.0")
    @Getter
    String registryIdLabel;

    /**
     * Returns the namespace to use for watching ConfigMaps.
     * If not explicitly configured, auto-detects the pod's namespace via the Kubernetes client,
     * or falls back to "default" if detection fails.
     */
    public String getEffectiveNamespace() {
        return namespace.orElseGet(() -> {
            String detected = kubernetesClient.getNamespace();
            return detected != null ? detected : "default";
        });
    }

    /**
     * Returns the label selector string for filtering ConfigMaps by registry ID.
     */
    public String getLabelSelector() {
        return registryIdLabel + "=" + getRegistryId();
    }

    @ConfigProperty(name = "apicurio.kubernetesops.watch.enabled", defaultValue = "true")
    @Info(category = CATEGORY_KUBERNETESOPS, experimental = true, description = """
            Enable Kubernetes Watch API for real-time ConfigMap change detection. \
            When enabled, changes are detected immediately via watch events \
            in addition to periodic polling.""", availableSince = "3.0.0")
    @Getter
    boolean watchEnabled;

    @ConfigProperty(name = "apicurio.kubernetesops.watch.reconnect-delay", defaultValue = "PT10S")
    @Info(category = CATEGORY_KUBERNETESOPS, experimental = true, description = """
            Base delay before reconnecting after watch failure. \
            Uses exponential backoff with a maximum of 5 minutes.""", availableSince = "3.0.0")
    @Getter
    Duration watchReconnectDelay;

    @Override
    public String getStorageName() {
        return "kubernetesops";
    }
}
