package io.apicurio.registry.operator;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

public class Configuration {

    private static final Config config = ConfigProvider.getConfig();

    private Configuration() {
    }

    public static String getAppImage() {
        return config.getOptionalValue("registry.app.image", String.class)
                .or(() -> config.getOptionalValue("related.image.registry.app.image", String.class))
                .orElseThrow(() -> new OperatorException("Required configuration option 'registry.app.image' is not set."));
    }

    public static String getUIImage() {
        return config.getOptionalValue("registry.ui.image", String.class)
                .or(() -> config.getOptionalValue("related.image.registry.ui.image", String.class))
                .orElseThrow(() -> new OperatorException("Required configuration option 'registry.ui.image' is not set."));
    }

    public static String getRegistryVersion() {
        return config.getOptionalValue("registry.version", String.class)
                .orElseThrow(() -> new OperatorException("Required configuration option 'registry.version' is not set."));
    }

    public static String getDefaultBaseHost() {
        return config.getOptionalValue("apicurio.operator.default-base-host", String.class)
                .map(v -> "." + v)
                .orElse("");
    }

    public static boolean isLeaderElectionEnabled() {
        // we are not running in k8s if the POD_NAMESPACE is not set, so leader election is not possible in that case
        if (!isDefaultNamespaceAvailable()) {
            return false;
        }
        return config.getOptionalValue("apicurio.operator.leader-election.enabled", Boolean.class)
                .orElse(true);
    }

    public static String getLeaderElectionLeaseName() {
        return config.getOptionalValue("apicurio.operator.leader-election.lease-name", String.class)
                .orElse("apicurio-registry-operator-lease");
    }

    public static String getLeaderElectionLeaseNamespace() {
        return config.getOptionalValue("apicurio.operator.leader-election.lease-namespace", String.class)
                .filter(namespace -> !namespace.isBlank())
                 .or(() -> java.util.Optional.ofNullable(System.getenv("POD_NAMESPACE"))
                         .filter(namespace -> !namespace.isBlank()))
                 .orElse("");
    }

    public static boolean isDefaultNamespaceAvailable() {
        return java.util.Optional.ofNullable(System.getenv("POD_NAMESPACE"))
                .filter(namespace -> !namespace.isBlank())
                .isPresent();
    }
}
