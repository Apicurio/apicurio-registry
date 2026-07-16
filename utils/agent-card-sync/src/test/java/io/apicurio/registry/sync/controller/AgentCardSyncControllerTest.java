package io.apicurio.registry.sync.controller;

import io.apicurio.registry.sync.client.ApicurioAgentCardClient;
import io.apicurio.registry.sync.client.ApicurioAgentCardClient.AgentCardVersionInfo;
import io.apicurio.registry.sync.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.kubernetes.client.KubernetesServerTestResource;
import java.util.Collections;
import java.util.List;

@QuarkusTest
@QuarkusTestResource(KubernetesServerTestResource.class)
public class AgentCardSyncControllerTest {


    @Inject
    AgentCardSyncController controller;

    @Inject
    KubernetesClient kubernetesClient;

    @InjectMock
    ApicurioAgentCardClient apicurioClient;

    @BeforeEach
    void cleanUp() {
        try {
            kubernetesClient.resources(ApicurioAgentCard.class).inNamespace("default").delete();
        } catch (Exception e) {
            // Ignore if CRD is not supported or registered in mock server
        }
    }


    @Test
    void testSyncNewCard() {
        String artifactId = "test-agent-1";
        AgentCardVersionInfo versionInfo = new AgentCardVersionInfo("default", artifactId, "1.0.0", 1L, 1L, 1000L);

        ApicurioAgentCardSpec spec = ApicurioAgentCardSpec.builder()
                .name("TestAgent")
                .description("A test agent card")
                .version("1.0.0")
                .supportedInterfaces(List.of(SupportedInterface.builder()
                        .url("https://example.com/agent")
                        .protocolBinding("http+json")
                        .protocolVersion("1.0")
                        .build()))
                .capabilities(AgentCapabilities.builder()
                        .streaming(false)
                        .pushNotifications(false)
                        .build())
                .skills(List.of(AgentSkill.builder()
                        .id("test-skill")
                        .name("Test Skill")
                        .description("A test skill")
                        .tags(List.of("tag1"))
                        .build()))
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .build();

        Mockito.when(apicurioClient.getAgentCardVersions()).thenReturn(List.of(versionInfo));
        Mockito.when(apicurioClient.isChanged(artifactId, 1L, 1L, 1000L)).thenReturn(true);
        Mockito.when(apicurioClient.fetchAgentCardSpec("default", artifactId, "1.0.0")).thenReturn(spec);

        // Run sync
        controller.sync();

        // Verify CRD was created in cluster
        List<ApicurioAgentCard> list = kubernetesClient.resources(ApicurioAgentCard.class).inNamespace("default").list().getItems();
        Assertions.assertEquals(1, list.size());
        ApicurioAgentCard crd = list.get(0);
        Assertions.assertEquals("test-agent-1", crd.getMetadata().getName());
        Assertions.assertEquals("true", crd.getMetadata().getAnnotations().get("apicurio.io/managed"));
        Assertions.assertEquals("test-agent-1", crd.getMetadata().getAnnotations().get("apicurio.io/artifact-id"));
        Assertions.assertEquals("TestAgent", crd.getSpec().getName());
        Assertions.assertEquals("Active", crd.getStatus().getSyncStatus());
    }

    @Test
    void testSyncStaleCard() {
        // First sync creates the card
        String artifactId = "test-agent-stale";
        String normalizedName = "test-agent-stale";
        
        ApicurioAgentCard crd = new ApicurioAgentCard();
        crd.setMetadata(new io.fabric8.kubernetes.api.model.ObjectMetaBuilder()
                .withName(normalizedName)
                .withNamespace("default")
                .addToAnnotations(AgentCardSyncController.MANAGED_ANNOTATION_KEY, AgentCardSyncController.MANAGED_ANNOTATION_VALUE)
                .addToAnnotations("apicurio.io/artifact-id", artifactId)
                .build());
        crd.setSpec(ApicurioAgentCardSpec.builder().name("StaleAgent").build());
        crd.setStatus(ApicurioAgentCardStatus.builder().syncStatus("Active").build());

        kubernetesClient.resources(ApicurioAgentCard.class).inNamespace("default").resource(crd).create();
        kubernetesClient.resources(ApicurioAgentCard.class).inNamespace("default").resource(crd).updateStatus(crd);

        // Mock empty list (card deleted in registry)
        Mockito.when(apicurioClient.getAgentCardVersions()).thenReturn(Collections.emptyList());

        // Run sync
        controller.sync();

        // Verify CRD is marked as Stale
        List<ApicurioAgentCard> list = kubernetesClient.resources(ApicurioAgentCard.class).inNamespace("default").list().getItems();
        Assertions.assertEquals(1, list.size());
        ApicurioAgentCard updated = list.get(0);
        Assertions.assertEquals("Stale", updated.getStatus().getSyncStatus());
        Assertions.assertTrue(updated.getStatus().getMessage().contains("deleted"));
        Mockito.verify(apicurioClient).evictCache(artifactId);
    }

    @Test
    void testSyncUpdateCardStableName() {
        String artifactId = "test-agent-update-stable";
        AgentCardVersionInfo v1 = new AgentCardVersionInfo("default", artifactId, "1", 1L, 1L, 1000L);
        ApicurioAgentCardSpec spec1 = ApicurioAgentCardSpec.builder().name("OriginalAgent").build();

        Mockito.when(apicurioClient.getAgentCardVersions()).thenReturn(List.of(v1));
        Mockito.when(apicurioClient.isChanged(artifactId, 1L, 1L, 1000L)).thenReturn(true);
        Mockito.when(apicurioClient.fetchAgentCardSpec("default", artifactId, "1")).thenReturn(spec1);

        // Run first sync
        controller.sync();

        // Verify CRD was created with normalized artifactId
        List<ApicurioAgentCard> list1 = kubernetesClient.resources(ApicurioAgentCard.class).inNamespace("default").list().getItems();
        Assertions.assertEquals(1, list1.size());
        Assertions.assertEquals("test-agent-update-stable", list1.get(0).getMetadata().getName());
        Assertions.assertEquals("OriginalAgent", list1.get(0).getSpec().getName());

        // Now mock version 2 with updated spec content (spec.name changed to "UpdatedAgent")
        AgentCardVersionInfo v2 = new AgentCardVersionInfo("default", artifactId, "2", 2L, 2L, 2000L);
        ApicurioAgentCardSpec spec2 = ApicurioAgentCardSpec.builder().name("UpdatedAgent").build();

        Mockito.when(apicurioClient.getAgentCardVersions()).thenReturn(List.of(v2));
        Mockito.when(apicurioClient.isChanged(artifactId, 2L, 2L, 2000L)).thenReturn(true);
        Mockito.when(apicurioClient.fetchAgentCardSpec("default", artifactId, "2")).thenReturn(spec2);

        // Run second sync
        controller.sync();

        // Verify only 1 CRD exists, and its spec name was updated while metadata name remains stable
        List<ApicurioAgentCard> list2 = kubernetesClient.resources(ApicurioAgentCard.class).inNamespace("default").list().getItems();
        Assertions.assertEquals(1, list2.size());
        ApicurioAgentCard finalCr = list2.get(0);
        Assertions.assertEquals("test-agent-update-stable", finalCr.getMetadata().getName());
        Assertions.assertEquals("UpdatedAgent", finalCr.getSpec().getName());
    }
}
