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

import static org.awaitility.Awaitility.await;

public final class OLMTestUtils {

    public static final String PACKAGE_NAME = "apicurio-registry-3";
    public static final String CATALOG_NAME = "apicurio-registry-operator-catalog";
    public static final String PROJECT_VERSION_PROP = "registry.version";
    public static final String PROJECT_ROOT_PROP = "test.operator.project-root";
    public static final String CATALOG_IMAGE_PROP = "test.operator.catalog-image";
    public static final String OLM_VERSION_PROP = "test.operator.olm-version";

    private OLMTestUtils() {
    }

    public static String getProjectVersion() {
        return ConfigProvider.getConfig().getValue(PROJECT_VERSION_PROP, String.class);
    }

    /**
     * The configured OLM version this test run targets (0 for OLM v0, 1 for OLM v1).
     */
    public static int getOlmVersion() {
        return ConfigProvider.getConfig().getOptionalValue(OLM_VERSION_PROP, Integer.class).orElse(0);
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
        await().ignoreExceptions()
                .until(() -> client.pods().inNamespace(namespace).list().getItems().stream()
                        .filter(pod -> pod.getMetadata().getName()
                                .startsWith("apicurio-registry-operator-catalog"))
                        .anyMatch(pod -> pod.getStatus().getConditions().stream().anyMatch(
                                c -> "Ready".equals(c.getType()) && "True".equals(c.getStatus()))));
        // A Ready pod is not immediately routable: the Service endpoints (and kube-proxy
        // rules) lag by a beat, and OLM's resolver otherwise fails with "no route to host".
        await().ignoreExceptions().until(() -> {
            var endpoints = client.endpoints().inNamespace(namespace)
                    .withName("apicurio-registry-operator-catalog").get();
            return endpoints != null && endpoints.getSubsets() != null
                    && endpoints.getSubsets().stream()
                            .anyMatch(s -> s.getAddresses() != null && !s.getAddresses().isEmpty());
        });
    }

    /**
     * Waits until the OLM v1 {@code ClusterCatalog} named {@code catalogName} reports a {@code Serving}
     * condition of {@code True}, meaning catalogd has finished unpacking it and its content is queryable.
     */
    @SuppressWarnings("unchecked")
    public static void waitForClusterCatalogServing(KubernetesClient client, String namespace,
            String catalogName) {
        await().ignoreExceptions().until(() -> {
            var cc = client.genericKubernetesResources("olm.operatorframework.io/v1", "ClusterCatalog")
                    .inNamespace(namespace)
                    .withName(catalogName)
                    .get();
            if (cc == null) {
                return false;
            }
            var conditions = (Collection<Map<String, Object>>) cc.get("status", "conditions");
            return conditions != null && conditions.stream()
                    .anyMatch(c -> "Serving".equals(c.get("type")) && "True".equals(c.get("status")));
        });
    }
}
