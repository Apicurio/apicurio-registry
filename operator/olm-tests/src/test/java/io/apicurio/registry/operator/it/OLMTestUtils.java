package io.apicurio.registry.operator.it;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.eclipse.microprofile.config.ConfigProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public final class OLMTestUtils {

    public static final String PACKAGE_NAME = "apicurio-registry-3";
    public static final String PROJECT_VERSION_PROP = "registry.version";
    public static final String PROJECT_ROOT_PROP = "test.operator.project-root";
    public static final String CATALOG_IMAGE_PROP = "test.operator.catalog-image";
    public static final String OLM_VERSION_PROP = "test.operator.olm-version";

    private OLMTestUtils() {
    }

    public static String getProjectVersion() {
        return ConfigProvider.getConfig().getValue(PROJECT_VERSION_PROP, String.class);
    }

    public static String getCatalogImage() {
        return ConfigProvider.getConfig().getValue(CATALOG_IMAGE_PROP, String.class);
    }

    public static String deriveMinorChannel(String version) {
        var lc = version.toLowerCase();
        var parts = lc.split("\\.");
        if (parts.length >= 2) {
            return parts[0] + "." + parts[1] + ".x";
        }
        return lc;
    }

    public static String deriveRollingChannel(String version) {
        var parts = version.toLowerCase().split("\\.");
        if (parts.length >= 1) {
            return parts[0] + ".x";
        }
        return version.toLowerCase();
    }

    public static String csvName(String version) {
        return PACKAGE_NAME + ".v" + version.toLowerCase();
    }

    public static String loadRawResource(String path) throws IOException {
        var projectRoot = ConfigProvider.getConfig().getValue(PROJECT_ROOT_PROP, String.class);
        var testDeployDir = Paths.get(projectRoot, "operator/olm-tests/src/test/deploy");
        return Files.readString(testDeployDir.resolve(path));
    }

    public static String replaceVars(String rawResource, String namespace) {
        return replaceVars(rawResource, namespace, Map.of());
    }

    public static String replaceVars(String rawResource, String namespace,
            Map<String, String> extraReplacements) {
        var projectVersion = getProjectVersion();
        var catalogImage = getCatalogImage();
        rawResource = rawResource.replace("${PLACEHOLDER_NAMESPACE}", namespace);
        rawResource = rawResource.replace("${PLACEHOLDER_CATALOG_NAMESPACE}", namespace);
        rawResource = rawResource.replace("${PLACEHOLDER_CATALOG_IMAGE}", catalogImage);
        rawResource = rawResource.replace("${PLACEHOLDER_PACKAGE_NAME}", PACKAGE_NAME);
        rawResource = rawResource.replace("${PLACEHOLDER_PACKAGE}", csvName(projectVersion));
        rawResource = rawResource.replace("${PLACEHOLDER_VERSION}", projectVersion);
        rawResource = rawResource.replace("${PLACEHOLDER_LC_VERSION}", projectVersion.toLowerCase());
        rawResource = rawResource.replace("${PLACEHOLDER_CHANNEL}", deriveMinorChannel(projectVersion));
        for (var entry : extraReplacements.entrySet()) {
            rawResource = rawResource.replace(entry.getKey(), entry.getValue());
        }
        return rawResource;
    }

    public static void createResource(KubernetesClient client, String namespace, String path)
            throws IOException {
        var raw = loadRawResource(path);
        client.resource(replaceVars(raw, namespace)).create();
    }

    public static void deleteResourceQuietly(KubernetesClient client, String namespace, String path) {
        try {
            var raw = loadRawResource(path);
            client.resource(replaceVars(raw, namespace)).delete();
        } catch (Exception e) {
            // ignore
        }
    }

    public static GenericKubernetesResource getPackageManifest(KubernetesClient client,
            String namespace) {
        return client
                .genericKubernetesResources("packages.operators.coreos.com/v1", "PackageManifest")
                .inNamespace(namespace).withName(PACKAGE_NAME).get();
    }

    @SuppressWarnings("unchecked")
    public static List<String> getChannelNames(GenericKubernetesResource pm) {
        var channels = (Collection<Map<String, Object>>) pm.get("status", "channels");
        return channels.stream().map(c -> (String) c.get("name")).toList();
    }

    @SuppressWarnings("unchecked")
    public static Collection<Map<String, Object>> getChannels(GenericKubernetesResource pm) {
        return (Collection<Map<String, Object>>) pm.get("status", "channels");
    }

    public static String getDefaultChannel(GenericKubernetesResource pm) {
        return (String) pm.get("status", "defaultChannel");
    }

    public static String getChannelCurrentCSV(GenericKubernetesResource pm, String channelName) {
        return getChannels(pm).stream().filter(c -> channelName.equals(c.get("name")))
                .map(c -> (String) c.get("currentCSV")).findFirst().orElse(null);
    }

    public static void waitForCatalogPodReady(KubernetesClient client, String namespace) {
        org.awaitility.Awaitility.await().ignoreExceptions()
                .until(() -> client.pods().inNamespace(namespace).list().getItems().stream()
                        .filter(pod -> pod.getMetadata().getName()
                                .startsWith("apicurio-registry-operator-catalog"))
                        .anyMatch(pod -> pod.getStatus().getConditions().stream().anyMatch(
                                c -> "Ready".equals(c.getType()) && "True".equals(c.getStatus()))));
    }
}
