package io.apicurio.registry.operator;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import java.util.Optional;

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

    public static String getConsolePluginImage() {
        return config.getOptionalValue("registry.console-plugin.image", String.class)
                .or(() -> config.getOptionalValue("related.image.registry.console-plugin.image", String.class))
                .orElseThrow(() -> new OperatorException("Required configuration option 'registry.console-plugin.image' is not set."));
    }

    public static String getGitOpsSyncImage() {
        return config.getOptionalValue("registry.gitops-sync.image", String.class)
                .or(() -> config.getOptionalValue("related.image.registry.gitops-sync.image", String.class))
                .orElseThrow(() -> new OperatorException("Required configuration option 'registry.gitops-sync.image' is not set."));
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
        if (getLeaderElectionLeaseNamespace().isEmpty()) {
            return false;
        }
        return config.getOptionalValue("apicurio.operator.leader-election.enabled", Boolean.class)
                .orElse(true);
    }

    public static String getLeaderElectionLeaseName() {
        return config.getOptionalValue("apicurio.operator.leader-election.lease-name", String.class)
                .orElse("apicurio-registry-operator-lease");
    }

    public static Optional<String> getLeaderElectionLeaseNamespace() {
        return config.getOptionalValue("apicurio.operator.leader-election.lease-namespace", String.class)
                .filter(namespace -> !namespace.isBlank())
                .or(Configuration::getControllerNamespace);
    }

    public static Optional<String> getControllerNamespace() {
        return Optional.ofNullable(System.getenv("POD_NAMESPACE"))
                .filter(namespace -> !namespace.isBlank());
    }
}
