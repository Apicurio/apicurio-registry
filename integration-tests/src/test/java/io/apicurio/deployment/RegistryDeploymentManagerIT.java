package io.apicurio.deployment;

import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.PodListBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientTimeoutException;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.util.concurrent.TimeUnit;

import static io.apicurio.deployment.KubernetesTestResources.TEST_NAMESPACE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("smoke")
class RegistryDeploymentManagerIT {

    @Test
    void allowsSnapshotReplicasToKeepMakingProgress() {
        var client = mock(KubernetesClient.class, RETURNS_DEEP_STUBS);
        var pods = pods(client);
        var timeout = mock(KubernetesClientTimeoutException.class);
        when(pods.waitUntilReady(360, TimeUnit.SECONDS)).thenThrow(timeout).thenThrow(timeout)
                .thenReturn(new PodBuilder().build());
        when(pods.list()).thenReturn(readyPods(0), readyPods(2));

        RegistryDeploymentManager.waitForAllPodsReady(client);

        verify(pods, times(3)).waitUntilReady(360, TimeUnit.SECONDS);
        verify(pods, times(2)).list();
    }

    @Test
    void stopsWhenNoAdditionalPodsBecomeReady() {
        var client = mock(KubernetesClient.class, RETURNS_DEEP_STUBS);
        var pods = pods(client);
        var timeout = mock(KubernetesClientTimeoutException.class);
        when(pods.waitUntilReady(360, TimeUnit.SECONDS)).thenThrow(timeout);
        when(pods.list()).thenReturn(readyPods(2));

        var failure = assertThrows(RuntimeException.class,
                () -> RegistryDeploymentManager.waitForAllPodsReady(client));
        assertTrue(failure.getMessage().contains("no longer making progress"));
        assertSame(timeout, failure.getCause());
        verify(pods, times(2)).waitUntilReady(360, TimeUnit.SECONDS);
    }

    @Test
    void boundsRetriesEvenWhenListingFails() {
        var client = mock(KubernetesClient.class, RETURNS_DEEP_STUBS);
        var pods = pods(client);
        when(pods.waitUntilReady(360, TimeUnit.SECONDS)).thenThrow(mock(KubernetesClientTimeoutException.class));
        when(pods.list()).thenThrow(new IllegalStateException("API unavailable"));

        var failure = assertThrows(RuntimeException.class,
                () -> RegistryDeploymentManager.waitForAllPodsReady(client));
        assertTrue(failure.getMessage().contains("after 5 attempts"));
        verify(pods, times(5)).waitUntilReady(360, TimeUnit.SECONDS);
    }

    @Test
    void aTransientListingFailureDoesNotMeanNoProgress() {
        var client = mock(KubernetesClient.class, RETURNS_DEEP_STUBS);
        var pods = pods(client);
        var timeout = mock(KubernetesClientTimeoutException.class);
        when(pods.waitUntilReady(360, TimeUnit.SECONDS)).thenThrow(timeout).thenThrow(timeout)
                .thenReturn(new PodBuilder().build());
        when(pods.list()).thenThrow(new IllegalStateException("API unavailable")).thenReturn(readyPods(2));

        RegistryDeploymentManager.waitForAllPodsReady(client);

        verify(pods, times(3)).waitUntilReady(360, TimeUnit.SECONDS);
    }

    @Test
    void countsOnlyReadyConditions() {
        var client = mock(KubernetesClient.class, RETURNS_DEEP_STUBS);
        var list = readyPods(2);
        list.getItems().add(new PodBuilder().withNewMetadata().withName("pending").endMetadata().build());
        list.getItems().add(new PodBuilder().withNewMetadata().withName("unready").endMetadata()
                .withNewStatus().addNewCondition().withType("Ready").withStatus("False").endCondition()
                .endStatus().build());
        when(pods(client).list()).thenReturn(list);

        assertEquals(2, RegistryDeploymentManager.countReadyPods(client));
    }

    private static PodList readyPods(int count) {
        var list = new PodListBuilder().build();
        for (int i = 0; i < count; i++) {
            list.getItems().add(new PodBuilder().withNewMetadata().withName("registry-" + i).endMetadata()
                    .withNewStatus().addNewCondition().withType("Ready").withStatus("True").endCondition()
                    .endStatus().build());
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    private static NonNamespaceOperation<Pod, PodList, PodResource> pods(KubernetesClient client) {
        NonNamespaceOperation<Pod, PodList, PodResource> pods = mock(NonNamespaceOperation.class);
        when(client.pods().inNamespace(TEST_NAMESPACE)).thenReturn(pods);
        return pods;
    }
}
