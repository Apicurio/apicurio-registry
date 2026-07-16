package io.apicurio.registry.sync.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;

import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.sync.client.ApicurioAgentCardClient;
import io.apicurio.registry.sync.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.vertx.core.Vertx;

public class VerifyEndToEnd {
    public static void main(String[] args) {
        System.out.println("Starting End-to-End Verification...");
        
        String registryUrl = "http://localhost:8081/apis/registry/v3";
        String groupId = "default";
        long uniqueTime = System.currentTimeMillis();
        String artifactId = "e2e-agent-card-" + uniqueTime;
        String k8sName = "e2e-agent-card-" + uniqueTime;
        String namespace = "default";

        Vertx vertx = Vertx.vertx();
        try (KubernetesClient k8sClient = new KubernetesClientBuilder().build()) {
            
            // Clean up K8s cluster
            try {
                k8sClient.resources(ApicurioAgentCard.class).inNamespace(namespace).withName(k8sName).delete();
                System.out.println("Cleaned up existing K8s CR.");
            } catch (Exception e) {}

            // Initialize Apicurio Registry SDK Client
            System.out.println("Initializing Apicurio Registry Client targeting: " + registryUrl);
            RegistryClientOptions options = RegistryClientOptions.create()
                    .registryUrl(registryUrl)
                    .vertx(vertx);
            RegistryClient registryClient = RegistryClientFactory.create(options);

            // Step 1: Post Agent Card to Apicurio Registry
            System.out.println("Step 1: Posting AGENT_CARD artifact to Apicurio Registry...");
            String agentJson = "{"
                    + "\"name\": \"E2EAgent\","
                    + "\"description\": \"End to End Verified Agent\","
                    + "\"version\": \"1.0.0\","
                    + "\"supportedInterfaces\": [{"
                    + "  \"url\": \"https://example.com/e2e-agent\","
                    + "  \"protocolBinding\": \"http+json\","
                    + "  \"protocolVersion\": \"1.0\""
                    + "}]"
                    + "}";

            CreateArtifact createArtifact = new CreateArtifact();
            createArtifact.setArtifactId(artifactId);
            createArtifact.setArtifactType("AGENT_CARD");
            
            CreateVersion createVersion = new CreateVersion();
            VersionContent versionContent = new VersionContent();
            versionContent.setContent(agentJson);
            versionContent.setContentType("application/json");
            createVersion.setContent(versionContent);
            createArtifact.setFirstVersion(createVersion);

            try {
                registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).delete();
            } catch (Exception e) {}

            try {
                registryClient.groups().byGroupId(groupId).artifacts().post(createArtifact);
                System.out.println("Successfully posted artifact to registry.");
            } catch (Exception e) {
                System.err.println("Exception type: " + e.getClass().getName());
                System.err.println("Exception message: " + e.getMessage());
                if (e instanceof com.microsoft.kiota.ApiException) {
                    com.microsoft.kiota.ApiException apiEx = (com.microsoft.kiota.ApiException) e;
                    System.err.println("HTTP Response Status: " + apiEx.getResponseStatusCode());
                }
                if (e instanceof io.apicurio.registry.rest.client.models.ProblemDetails) {
                    io.apicurio.registry.rest.client.models.ProblemDetails pd = (io.apicurio.registry.rest.client.models.ProblemDetails) e;
                    System.err.println("Problem Title: " + pd.getTitle());
                    System.err.println("Problem Detail: " + pd.getDetail());
                }
                throw e;
            }

            // Wait for artifact to be indexed
            System.out.println("Waiting for artifact to be indexed...");
            boolean indexed = false;
            for (int i = 0; i < 15; i++) {
                var results = registryClient.search().versions().get(config -> {
                    config.queryParameters.artifactType = "AGENT_CARD";
                });
                if (results != null && results.getVersions() != null && !results.getVersions().isEmpty()) {
                    boolean found = results.getVersions().stream()
                            .anyMatch(v -> artifactId.equals(v.getArtifactId()));
                    if (found) {
                        indexed = true;
                        break;
                    }
                }
                Thread.sleep(1000);
            }
            if (!indexed) {
                throw new RuntimeException("E2E Verification Failed: Artifact was not indexed by Apicurio Registry.");
            }
            System.out.println("Artifact indexed successfully.");

            // Initialize our Sync Client & Controller programmatically
            ApicurioAgentCardClient syncClient = new ApicurioAgentCardClient();
            syncClient.registryUrl = registryUrl;
            syncClient.vertx = vertx;
            syncClient.objectMapper = new ObjectMapper();

            System.out.println("Invoking syncClient.getAgentCardVersions() directly...");
            var cards = syncClient.getAgentCardVersions();
            System.out.println("Direct syncClient cards count: " + cards.size());
            for (var c : cards) {
                System.out.println("  ArtifactId: " + c.artifactId + ", version: " + c.version + ", globalId: " + c.globalId);
                try {
                    ApicurioAgentCardSpec spec = syncClient.fetchAgentCardSpec(c.groupId, c.artifactId, c.version);
                    System.out.println("    Spec: " + (spec == null ? "null" : spec.getName() + " - " + spec.getDescription()));
                } catch (Exception ex) {
                    System.out.println("    Failed to fetch spec: " + ex.getMessage());
                    ex.printStackTrace();
                }
            }

            AgentCardSyncController controller = new AgentCardSyncController();
            controller.apicurioClient = syncClient;
            controller.kubernetesClient = k8sClient;
            controller.namespace = namespace;

            // Step 2: Run synchronization cycle
            System.out.println("Step 2: Running sync controller cycle...");
            controller.sync();

            // Assert CR was created on K8s cluster
            ApicurioAgentCard cr = k8sClient.resources(ApicurioAgentCard.class)
                    .inNamespace(namespace)
                    .withName(k8sName)
                    .get();
            if (cr == null) {
                throw new RuntimeException("E2E Verification Failed: CR was not created on the cluster.");
            }
            System.out.println("SUCCESS: ApicurioAgentCard CR created in K8s. Resource Name: " + cr.getMetadata().getName() + ", Spec Name: " + cr.getSpec().getName());
            if (!"Active".equals(cr.getStatus().getSyncStatus())) {
                throw new RuntimeException("E2E Verification Failed: CR status is not Active.");
            }

            // Step 3: Update version in Apicurio Registry
            System.out.println("Step 3: Updating artifact in Apicurio Registry...");
            String updatedAgentJson = "{"
                    + "\"name\": \"E2EAgent-Updated\","
                    + "\"description\": \"End to End Verified Agent - Version 2\","
                    + "\"version\": \"2.0.0\","
                    + "\"supportedInterfaces\": [{"
                    + "  \"url\": \"https://example.com/e2e-agent-v2\","
                    + "  \"protocolBinding\": \"http+json\","
                    + "  \"protocolVersion\": \"2.0\""
                    + "}]"
                    + "}";

            CreateVersion updateVersion = new CreateVersion();
            VersionContent updateContent = new VersionContent();
            updateContent.setContent(updatedAgentJson);
            updateContent.setContentType("application/json");
            updateVersion.setContent(updateContent);

            registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().post(updateVersion);
            System.out.println("Posted version 2 to registry.");

            // Wait for version update to be indexed
            System.out.println("Waiting for version update to be indexed...");
            boolean versionIndexed = false;
            for (int i = 0; i < 15; i++) {
                var results = registryClient.search().versions().get(config -> {
                    config.queryParameters.artifactType = "AGENT_CARD";
                });
                if (results != null && results.getVersions() != null) {
                    boolean found = results.getVersions().stream()
                            .anyMatch(v -> artifactId.equals(v.getArtifactId()) && "2".equals(v.getVersion()));
                    if (found) {
                        versionIndexed = true;
                        break;
                    }
                }
                Thread.sleep(1000);
            }
            if (!versionIndexed) {
                throw new RuntimeException("E2E Verification Failed: Updated version was not indexed by Apicurio Registry.");
            }
            System.out.println("Updated version indexed successfully.");

            // Run sync controller cycle again
            System.out.println("Running sync controller cycle after update...");
            controller.sync();

            // Assert CR spec was updated on K8s cluster
            ApicurioAgentCard updatedCr = k8sClient.resources(ApicurioAgentCard.class)
                    .inNamespace(namespace)
                    .withName(k8sName)
                    .get();
            if (updatedCr == null || !"2.0.0".equals(updatedCr.getSpec().getVersion())) {
                throw new RuntimeException("E2E Verification Failed: CR spec was not updated on the cluster.");
            }
            System.out.println("SUCCESS: ApicurioAgentCard CR updated in K8s. Resource Name: " + updatedCr.getMetadata().getName() + ", Spec Name: " + updatedCr.getSpec().getName());

            // Step 4: Delete card from Apicurio Registry
            System.out.println("Step 4: Deleting artifact from Apicurio Registry...");
            try {
                registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).delete();
                System.out.println("Deleted artifact from registry.");
            } catch (Exception e) {
                System.err.println("Exception type: " + e.getClass().getName());
                System.err.println("Exception message: " + e.getMessage());
                if (e instanceof com.microsoft.kiota.ApiException) {
                    com.microsoft.kiota.ApiException apiEx = (com.microsoft.kiota.ApiException) e;
                    System.err.println("HTTP Response Status: " + apiEx.getResponseStatusCode());
                }
                if (e instanceof io.apicurio.registry.rest.client.models.ProblemDetails) {
                    io.apicurio.registry.rest.client.models.ProblemDetails pd = (io.apicurio.registry.rest.client.models.ProblemDetails) e;
                    System.err.println("Problem Title: " + pd.getTitle());
                    System.err.println("Problem Detail: " + pd.getDetail());
                }
                throw e;
            }

            // Wait for deletion to be indexed
            System.out.println("Waiting for deletion to be indexed...");
            boolean deletionIndexed = false;
            for (int i = 0; i < 15; i++) {
                var results = registryClient.search().versions().get(config -> {
                    config.queryParameters.artifactType = "AGENT_CARD";
                });
                if (results == null || results.getVersions() == null || results.getVersions().isEmpty()) {
                    deletionIndexed = true;
                    break;
                }
                boolean found = results.getVersions().stream()
                        .anyMatch(v -> artifactId.equals(v.getArtifactId()));
                if (!found) {
                    deletionIndexed = true;
                    break;
                }
                Thread.sleep(1000);
            }
            if (!deletionIndexed) {
                throw new RuntimeException("E2E Verification Failed: Deleted artifact was still returned by search API.");
            }
            System.out.println("Deletion indexed successfully.");

            // Run sync controller cycle again
            System.out.println("Running sync controller cycle after deletion...");
            controller.sync();

            // Assert CR status is marked as Stale on K8s cluster
            ApicurioAgentCard staleCr = k8sClient.resources(ApicurioAgentCard.class)
                    .inNamespace(namespace)
                    .withName(k8sName)
                    .get();
            if (staleCr == null || !"Stale".equals(staleCr.getStatus().getSyncStatus())) {
                throw new RuntimeException("E2E Verification Failed: CR was not marked as Stale on deletion.");
            }
            System.out.println("SUCCESS: ApicurioAgentCard CR marked as Stale in K8s: " + staleCr.getStatus().getMessage());

            // Clean up
            k8sClient.resources(ApicurioAgentCard.class).inNamespace(namespace).withName(k8sName).delete();
            System.out.println("E2E VERIFICATION COMPLETED SUCCESSFULLY!");
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        } finally {
            vertx.close();
            System.exit(0);
        }
    }
}
