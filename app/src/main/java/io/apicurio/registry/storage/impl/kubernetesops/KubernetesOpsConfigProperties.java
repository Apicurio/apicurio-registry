package io.apicurio.registry.storage.impl.kubernetesops;

import io.apicurio.common.apps.config.Info;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

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
    @Info(category = CATEGORY_KUBERNETESOPS, description = "Kubernetes namespace to watch for ConfigMaps. Defaults to the current namespace if not specified.", availableSince = "3.0.0")
    @Getter
    Optional<String> namespace;

    @ConfigProperty(name = "apicurio.kubernetesops.label.registry-id", defaultValue = "apicurio.io/registry-id")
    @Info(category = CATEGORY_KUBERNETESOPS, description = "Label key used to identify ConfigMaps belonging to this registry instance.", availableSince = "3.0.0")
    @Getter
    String registryIdLabel;

    @ConfigProperty(name = "apicurio.kubernetesops.label.type", defaultValue = "apicurio.io/type")
    @Info(category = CATEGORY_KUBERNETESOPS, description = "Label key used to identify the type of data in the ConfigMap.", availableSince = "3.0.0")
    @Getter
    String typeLabel;

    /**
     * Returns the namespace to use for watching ConfigMaps.
     * Falls back to the default namespace if not explicitly configured.
     */
    public String getEffectiveNamespace() {
        return namespace.orElse("default");
    }
}
