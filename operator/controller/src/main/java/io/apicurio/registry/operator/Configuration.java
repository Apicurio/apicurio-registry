package io.apicurio.registry.operator;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import java.util.Optional;
import java.util.OptionalInt;

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

    public static Optional<String> getConsolePluginImage() {
        return config.getOptionalValue("registry.console-plugin.image", String.class)
                .or(() -> config.getOptionalValue("related.image.registry.console-plugin.image", String.class));
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

    /**
     * Return the image with its tag replaced by the given version. If version is null or blank, returns the
     * base image unchanged.
     */
    public static String getImageForVersion(String baseImage, String version) {
        if (version == null || version.isBlank()) {
            return baseImage;
        }
        int colon = baseImage.lastIndexOf(':');
        String repo = (colon > 0) ? baseImage.substring(0, colon) : baseImage;
        return repo + ":" + version;
    }

    /**
     * Compare two version strings (e.g. "3.0.3" or "3.3.1-SNAPSHOT"). Strips any suffix after a hyphen
     * before comparing. Returns empty if either version is not a valid numeric semver. Otherwise returns
     * negative if v1 < v2, zero if equal, positive if v1 > v2.
     */
    public static OptionalInt compareVersions(String v1, String v2) {
        try {
            String[] parts1 = v1.split("-")[0].split("\\.");
            String[] parts2 = v2.split("-")[0].split("\\.");
            int len = Math.max(parts1.length, parts2.length);
            for (int i = 0; i < len; i++) {
                int n1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
                int n2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;
                if (n1 != n2) {
                    return OptionalInt.of(Integer.compare(n1, n2));
                }
            }
            return OptionalInt.of(0);
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
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
