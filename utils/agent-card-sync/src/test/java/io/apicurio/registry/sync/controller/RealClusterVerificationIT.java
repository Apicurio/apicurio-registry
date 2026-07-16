package io.apicurio.registry.sync.controller;

import io.apicurio.registry.sync.client.ApicurioAgentCardClient;
import io.apicurio.registry.sync.client.ApicurioAgentCardClient.AgentCardVersionInfo;
import io.apicurio.registry.sync.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

@QuarkusTest
@org.junit.jupiter.api.Disabled("Requires a real Kubernetes cluster (e.g. kind); not intended to run during the build.")
public class RealClusterVerificationIT {

    @Inject
    AgentCardSyncController controller;

    @Inject
    KubernetesClient kubernetesClient;

    @InjectMock
    ApicurioAgentCardClient apicurioClient;

    @Test
    void verifyRealClusterSync() {
        // 1. Setup mock Apicurio response
        String artifactId = "real-agent-card";
        AgentCardVersionInfo versionInfo = new AgentCardVersionInfo("default", artifactId, "1.0.0", 1L, 1L, 1000L);

        ApicurioAgentCardSpec spec = ApicurioAgentCardSpec.builder()
                .name("RealClusterAgent")
                .description("Verified on real cluster")
                .version("1.0.0")
                .supportedInterfaces(List.of(SupportedInterface.builder()
                        .url("https://example.com/agent")
                        .protocolBinding("http+json")
                        .protocolVersion("1.0")
                        .build()))
                .build();

        Mockito.when(apicurioClient.getAgentCardVersions()).thenReturn(List.of(versionInfo));
        Mockito.when(apicurioClient.isChanged(artifactId, 1L, 1L, 1000L)).thenReturn(true);
        Mockito.when(apicurioClient.fetchAgentCardSpec("default", artifactId, "1.0.0")).thenReturn(spec);

        // Clean up any existing CRD instance for this artifactId
        try {
            kubernetesClient.resources(ApicurioAgentCard.class).inNamespace("default").withName(artifactId).delete();
        } catch (Exception e) {
            // Ignore if resource does not exist yet
        }

        // 2. Run the sync controller
        controller.sync();

        // 3. Assert CRD was created in the real cluster!
        List<ApicurioAgentCard> list = kubernetesClient.resources(ApicurioAgentCard.class).inNamespace("default").list().getItems();
        Assertions.assertEquals(1, list.size());
        
        ApicurioAgentCard crd = list.get(0);
        Assertions.assertEquals("real-agent-card", crd.getMetadata().getName());
        Assertions.assertEquals("RealClusterAgent", crd.getSpec().getName());
        Assertions.assertEquals("Active", crd.getStatus().getSyncStatus());
        
        System.out.println("VERIFICATION SUCCESSFUL: ApicurioAgentCard CR created in Kind cluster!");
    }
}
