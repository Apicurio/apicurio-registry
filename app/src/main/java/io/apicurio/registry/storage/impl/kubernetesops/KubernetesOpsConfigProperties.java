package io.apicurio.registry.storage.impl.kubernetesops;

import io.apicurio.common.apps.config.Info;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.Optional;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_KUBERNETESOPS;

@ApplicationScoped
public class KubernetesOpsConfigProperties {

    @ConfigProperty(name = "apicurio.kubernetesops.id")
    @Info(category = CATEGORY_KUBERNETESOPS, description = "Identifier of this Registry instance. Only ConfigMaps with a label matching this identifier will be loaded.", availableSince = "3.0.0")
    Optional<String> registryId;

    public String getRegistryId() {
        return registryId.orElseThrow(() -> new IllegalStateException(
                "apicurio.kubernetesops.id must be configured when using kubernetesops storage"));
    }

    @ConfigProperty(name = "apicurio.kubernetesops.namespace")
    @Info(category = CATEGORY_KUBERNETESOPS, description = "Kubernetes namespace to watch for ConfigMaps. Defaults to the \"default\" namespace if not specified.", availableSince = "3.0.0")
    @Getter
    Optional<String> namespace;

    @ConfigProperty(name = "apicurio.kubernetesops.label.registry-id", defaultValue = "apicurio.io/registry-id")
    @Info(category = CATEGORY_KUBERNETESOPS, description = "Label key used to identify ConfigMaps belonging to this registry instance.", availableSince = "3.0.0")
    @Getter
    String registryIdLabel;

    /**
     * Returns the namespace to use for watching ConfigMaps.
     * Defaults to the "default" Kubernetes namespace if not explicitly configured.
     */
    public String getEffectiveNamespace() {
        return namespace.orElse("default");
    }

    @ConfigProperty(name = "apicurio.kubernetesops.watch.enabled", defaultValue = "true")
    @Info(category = CATEGORY_KUBERNETESOPS, description = "Enable Kubernetes Watch API for real-time ConfigMap change detection.", availableSince = "3.0.0")
    @Getter
    boolean watchEnabled;

    @ConfigProperty(name = "apicurio.kubernetesops.watch.reconnect-delay", defaultValue = "PT10S")
    @Info(category = CATEGORY_KUBERNETESOPS, description = "Base delay before reconnecting after watch failure. Uses exponential backoff.", availableSince = "3.0.0")
    @Getter
    Duration watchReconnectDelay;
}
