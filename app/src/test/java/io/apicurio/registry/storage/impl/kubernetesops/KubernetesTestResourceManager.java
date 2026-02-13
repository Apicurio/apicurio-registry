package io.apicurio.registry.storage.impl.kubernetesops;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.kubernetes.client.KubernetesServerTestResource;
import lombok.Getter;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Objects.requireNonNull;

/**
 * Test resource manager for KubernetesOps storage tests.
 * Uses the Quarkus Kubernetes mock server test resource.
 */
public class KubernetesTestResourceManager implements QuarkusTestResourceLifecycleManager {

    private static final String NAMESPACE = "test-namespace";
    private static final String REGISTRY_ID = "test";
    private static final String REGISTRY_ID_LABEL = "apicurio.io/registry-id";

    @Getter
    private static KubernetesServerTestResource kubernetesServerTestResource;

    @Getter
    private static KubernetesMockConfigMapStore configMapStore;

    @Getter
    private static KubernetesClient client;

    @Override
    public Map<String, String> start() {
        kubernetesServerTestResource = new KubernetesServerTestResource();
        Map<String, String> props = kubernetesServerTestResource.start();

        Map<String, String> result = new HashMap<>(props);
        result.put("apicurio.kubernetesops.id", REGISTRY_ID);
        result.put("apicurio.kubernetesops.namespace", NAMESPACE);
        result.put("apicurio.kubernetesops.refresh.every", "5s");

        return result;
    }

    @Override
    public void inject(TestInjector testInjector) {
        kubernetesServerTestResource.inject(testInjector);
    }

    @Override
    public void stop() {
        if (kubernetesServerTestResource != null) {
            kubernetesServerTestResource.stop();
            kubernetesServerTestResource = null;
        }
        configMapStore = null;
        client = null;
    }

    /**
     * Initialize the config map store with the injected client.
     * Must be called from the test after injection.
     */
    public static void initializeConfigMapStore(KubernetesClient kubernetesClient) {
        client = kubernetesClient;
        configMapStore = new KubernetesMockConfigMapStore(kubernetesClient, NAMESPACE, REGISTRY_ID, REGISTRY_ID_LABEL);
    }

    /**
     * Helper class to manage mock ConfigMaps for testing.
     */
    public static class KubernetesMockConfigMapStore {
        private final KubernetesClient client;
        private final String namespace;
        private final String registryId;
        private final String registryIdLabel;
        private final AtomicInteger configMapCounter = new AtomicInteger(0);

        public KubernetesMockConfigMapStore(KubernetesClient client, String namespace,
                String registryId, String registryIdLabel) {
            this.client = client;
            this.namespace = namespace;
            this.registryId = registryId;
            this.registryIdLabel = registryIdLabel;
        }

        /**
         * Loads test data from a resource directory, converting files to ConfigMaps.
         * Files are stored with their relative paths as data keys to maintain
         * path resolution compatibility with GitOps storage.
         */
        public void load(String sourceDir) {
            try {
                // Clear existing ConfigMaps
                clear();

                var sourcePath = Path.of(
                        requireNonNull(Thread.currentThread().getContextClassLoader().getResource(sourceDir))
                                .toURI());

                Collection<File> files = FileUtils.listFiles(sourcePath.toFile(),
                        new String[]{"yaml", "yml", "json"}, true);

                // Create a single ConfigMap with all files using relative paths as keys
                Map<String, String> configMapData = new HashMap<>();

                for (File file : files) {
                    // Use the relative path as the data key (e.g., "test/artifact-petstore.yaml")
                    String relativePath = sourcePath.relativize(file.toPath()).toString();
                    // Normalize path separators
                    String dataKey = relativePath.replace('\\', '/');
                    String content = FileUtils.readFileToString(file, StandardCharsets.UTF_8);

                    configMapData.put(dataKey, content);
                }

                if (!configMapData.isEmpty()) {
                    String configMapName = "registry-data-" + configMapCounter.incrementAndGet();
                    ConfigMap configMap = new ConfigMapBuilder()
                            .withNewMetadata()
                                .withName(configMapName)
                                .withNamespace(namespace)
                                .addToLabels(registryIdLabel, registryId)
                            .endMetadata()
                            .withData(configMapData)
                            .build();

                    client.configMaps().inNamespace(namespace).resource(configMap).create();
                }

            } catch (IOException | URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * Clears all ConfigMaps.
         */
        public void clear() {
            client.configMaps()
                    .inNamespace(namespace)
                    .withLabel(registryIdLabel, registryId)
                    .delete();
        }
    }
}
