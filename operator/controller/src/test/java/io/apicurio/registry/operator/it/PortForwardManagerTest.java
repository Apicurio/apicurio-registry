package io.apicurio.registry.operator.it;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.LocalPortForward;
import io.fabric8.kubernetes.client.dsl.ServiceResource;
import io.fabric8.kubernetes.client.dsl.PodResource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("smoke")
class PortForwardManagerTest {

    @Test
    void tracksAndClosesTheAllocatedPort() throws Exception {
        var client = mock(KubernetesClient.class, RETURNS_DEEP_STUBS);
        var first = mock(LocalPortForward.class);
        var second = mock(LocalPortForward.class);
        when(first.getLocalPort()).thenReturn(41001);
        when(second.getLocalPort()).thenReturn(41002);
        var service = mock(ServiceResource.class);
        when(client.services().withName("registry")).thenReturn(service);
        when(service.portForward(8080, 0)).thenReturn(first, second);

        try (var manager = new PortForwardManager(client)) {
            assertThat(manager.startServicePortForward("registry", 8080)).isEqualTo(41001);
            assertThat(manager.startServicePortForward("registry", 8080)).isEqualTo(41002);
            assertThat(manager.getPortForward(41001)).isSameAs(first);
            assertThat(manager.getPortForward(41002)).isSameAs(second);
            manager.stop(41001);
            verify(first).close();
            assertThat(manager.getPortForward(41001)).isNull();
        }
        verify(second).close();
        verify(client).close();
    }

    @Test
    void preservesExplicitPortsAndRejectsDuplicates() {
        var client = mock(KubernetesClient.class, RETURNS_DEEP_STUBS);
        var forward = mock(LocalPortForward.class);
        when(forward.getLocalPort()).thenReturn(41003);
        var pod = mock(PodResource.class);
        when(client.pods().withName("registry-0")).thenReturn(pod);
        when(pod.portForward(8080, 41003)).thenReturn(forward);

        try (var manager = new PortForwardManager(client)) {
            assertThat(manager.startPodPortForward("registry-0", 8080, 41003)).isEqualTo(41003);
            assertThatThrownBy(() -> manager.startServicePortForward("registry", 8080, 41003))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("41003");
        }
    }

    @Test
    void propagatesForwardingFailures() {
        var client = mock(KubernetesClient.class, RETURNS_DEEP_STUBS);
        var failure = new IllegalStateException("Unable to port forward");
        var service = mock(ServiceResource.class);
        when(client.services().withName("registry")).thenReturn(service);
        when(service.portForward(8080, 0)).thenThrow(failure);
        try (var manager = new PortForwardManager(client)) {
            assertThatThrownBy(() -> manager.startServicePortForward("registry", 8080)).isSameAs(failure);
            assertThat(manager.getPortForward(0)).isNull();
        }
    }
}
