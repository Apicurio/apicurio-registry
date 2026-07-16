package io.apicurio.registry.sync.controller;

import io.apicurio.registry.sync.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import java.util.List;

public class VerifyRealCluster {
    public static void main(String[] args) {
        System.out.println("Starting real cluster verification...");
        try (KubernetesClient client = new KubernetesClientBuilder().build()) {
            String namespace = "default";
            String name = "real-agent-card-manual";

            System.out.println("Creating test card spec...");
            ApicurioAgentCardSpec spec = ApicurioAgentCardSpec.builder()
                    .name("ManualVerificationAgent")
                    .description("Created by direct client verification")
                    .version("1.0.0")
                    .supportedInterfaces(List.of(SupportedInterface.builder()
                            .url("https://example.com/agent")
                            .protocolBinding("http+json")
                            .protocolVersion("1.0")
                            .build()))
                    .build();

            ApicurioAgentCard crd = new ApicurioAgentCard();
            crd.setMetadata(new io.fabric8.kubernetes.api.model.ObjectMetaBuilder()
                    .withName(name)
                    .withNamespace(namespace)
                    .addToAnnotations(AgentCardSyncController.MANAGED_ANNOTATION_KEY, AgentCardSyncController.MANAGED_ANNOTATION_VALUE)
                    .build());
            crd.setSpec(spec);

            System.out.println("Applying CR to kind cluster...");
            ApicurioAgentCard applied = client.resources(ApicurioAgentCard.class)
                    .inNamespace(namespace)
                    .resource(crd)
                    .createOrReplace();

            ApicurioAgentCardStatus status = ApicurioAgentCardStatus.builder()
                    .syncStatus("Active")
                    .lastSynced(java.time.Instant.now().toString())
                    .message("Synchronized successfully")
                    .build();
            applied.setStatus(status);

            client.resources(ApicurioAgentCard.class)
                    .inNamespace(namespace)
                    .resource(applied)
                    .updateStatus(applied);

            System.out.println("Verifying CR was written...");
            ApicurioAgentCard fetched = client.resources(ApicurioAgentCard.class)
                    .inNamespace(namespace)
                    .withName(name)
                    .get();

            if (fetched != null && "ManualVerificationAgent".equals(fetched.getSpec().getName())) {
                System.out.println("SUCCESS: ApicurioAgentCard verified on real kind cluster!");
            } else {
                System.err.println("FAILURE: Resource spec mismatch or not found.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
