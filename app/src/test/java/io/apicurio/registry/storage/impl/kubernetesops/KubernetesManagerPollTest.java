package io.apicurio.registry.storage.impl.kubernetesops;

import io.apicurio.registry.storage.impl.polling.PollingResult;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ConfigMapList;
import io.fabric8.kubernetes.api.model.ConfigMapListBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies that {@link KubernetesManager#poll()} reports a change only when the selected ConfigMaps
 * change, not when the cluster-wide list resourceVersion advances.
 */
class KubernetesManagerPollTest {

    private KubernetesManager manager;
    private KubernetesClient client;
    private FilterWatchListDeletable<ConfigMap, ConfigMapList, Resource<ConfigMap>> selected;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        client = mock(KubernetesClient.class);
        MixedOperation<ConfigMap, ConfigMapList, Resource<ConfigMap>> configMaps = mock(MixedOperation.class);
        NonNamespaceOperation<ConfigMap, ConfigMapList, Resource<ConfigMap>> inNamespace =
                mock(NonNamespaceOperation.class);
        selected = mock(FilterWatchListDeletable.class);
        when(client.configMaps()).thenReturn(configMaps);
        when(configMaps.inNamespace("registry")).thenReturn(inNamespace);
        when(inNamespace.withLabelSelector("app=registry")).thenReturn(selected);
        KubernetesOpsConfig config = mock(KubernetesOpsConfig.class);
        when(config.getEffectiveNamespace()).thenReturn("registry");
        when(config.getLabelSelector()).thenReturn("app=registry");

        manager = new KubernetesManager();
        manager.log = LoggerFactory.getLogger(KubernetesManagerPollTest.class);
        manager.config = config;
        manager.kubernetesClient = client;
    }

    @Test
    void pollReportsChangesOnlyWhenTheSelectedConfigMapsChange() throws Exception {
        returnList("354", configMap("registry-data", "10"));
        PollingResult<KubernetesOpsMarker> first = manager.poll();
        assertEquals(true, first.isHasChanges());
        first.commit();

        // Unrelated writes in the cluster advance the list resourceVersion only.
        returnList("383", configMap("registry-data", "10"));
        assertEquals(false, manager.poll().isHasChanges());

        returnList("411", configMap("registry-data", "11"));
        assertEquals(true, manager.poll().isHasChanges());
    }

    private void returnList(String listResourceVersion, ConfigMap... items) {
        ConfigMapList list = new ConfigMapListBuilder().withNewMetadata()
                .withResourceVersion(listResourceVersion).endMetadata().withItems(items).build();
        when(selected.list()).thenReturn(list);
    }

    private static ConfigMap configMap(String name, String resourceVersion) {
        return new ConfigMapBuilder().withNewMetadata().withName(name).withResourceVersion(resourceVersion)
                .endMetadata().build();
    }
}
