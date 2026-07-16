package io.apicurio.registry.sync.controller;

import io.apicurio.registry.sync.client.ApicurioAgentCardClient;
import io.apicurio.registry.sync.client.ApicurioAgentCardClient.AgentCardVersionInfo;
import io.apicurio.registry.sync.model.ApicurioAgentCard;
import io.apicurio.registry.sync.model.ApicurioAgentCardSpec;
import io.apicurio.registry.sync.model.ApicurioAgentCardStatus;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class AgentCardSyncController {

    private static final Logger log = LoggerFactory.getLogger(AgentCardSyncController.class);

    public static final String MANAGED_ANNOTATION_KEY = "apicurio.io/managed";
    public static final String MANAGED_ANNOTATION_VALUE = "true";

    @ConfigProperty(name = "apicurio.sync.namespace", defaultValue = "default")
    String namespace;

    @Inject
    ApicurioAgentCardClient apicurioClient;

    @Inject
    KubernetesClient kubernetesClient;

    @Scheduled(every = "${apicurio.sync.interval:10s}", delay = 2, delayUnit = java.util.concurrent.TimeUnit.SECONDS, concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void sync() {
        log.info("Starting synchronization cycle...");
        try {
            List<AgentCardVersionInfo> activeCards = apicurioClient.getAgentCardVersions();
            Set<String> activeK8sNames = new HashSet<>();

            // 1. Process active cards
            for (AgentCardVersionInfo cardInfo : activeCards) {
                String k8sName = normalizeK8sName(cardInfo.artifactId);
                activeK8sNames.add(k8sName);

                boolean changed = apicurioClient.isChanged(cardInfo.artifactId, cardInfo.globalId, cardInfo.contentId, cardInfo.modifiedOn);
                if (changed) {
                    log.info("Change detected for artifact: {}. Fetching spec...", cardInfo.artifactId);
                    ApicurioAgentCardSpec spec = apicurioClient.fetchAgentCardSpec(cardInfo.groupId, cardInfo.artifactId, cardInfo.version);
                    if (spec != null) {
                        syncToKubernetes(k8sName, cardInfo.artifactId, spec);
                        apicurioClient.updateCache(cardInfo.artifactId, cardInfo.globalId, cardInfo.contentId, cardInfo.modifiedOn);
                    }
                } else {
                    log.debug("Artifact: {} is up to date, skipping", cardInfo.artifactId);
                }
            }

            // 2. Mark deleted/deprecated cards as stale
            handleStaleCards(activeK8sNames);

            log.info("Synchronization cycle completed successfully.");
        } catch (Exception e) {
            log.error("Error occurred during synchronization cycle", e);
        }
    }

    private void syncToKubernetes(String name, String artifactId, ApicurioAgentCardSpec spec) {
        log.info("Applying ApicurioAgentCard CRD to Kubernetes: {}/{}", namespace, name);

        ApicurioAgentCard crd = new ApicurioAgentCard();
        crd.setMetadata(new ObjectMetaBuilder()
                .withName(name)
                .withNamespace(namespace)
                .addToAnnotations(MANAGED_ANNOTATION_KEY, MANAGED_ANNOTATION_VALUE)
                .addToAnnotations("apicurio.io/artifact-id", artifactId)
                .build());
        crd.setSpec(spec);

        // Perform createOrReplace to create/update the CRD
        ApicurioAgentCard applied = kubernetesClient.resources(ApicurioAgentCard.class)
                .inNamespace(namespace)
                .resource(crd)
                .createOrReplace();


        // Update status indicating active synchronization
        ApicurioAgentCardStatus status = ApicurioAgentCardStatus.builder()
                .syncStatus("Active")
                .lastSynced(Instant.now().toString())
                .message("Synchronized successfully with Apicurio Registry")
                .build();
        applied.setStatus(status);

        kubernetesClient.resources(ApicurioAgentCard.class)
                .inNamespace(namespace)
                .resource(applied)
                .updateStatus(applied);
        
        log.info("Successfully applied and updated status for ApicurioAgentCard: {}", name);
    }

    private void handleStaleCards(Set<String> activeK8sNames) {
        log.debug("Scanning for stale ApicurioAgentCard CRDs in namespace: {}", namespace);
        try {
            List<ApicurioAgentCard> currentCRDs = kubernetesClient.resources(ApicurioAgentCard.class)
                    .inNamespace(namespace)
                    .list()
                    .getItems();

            List<ApicurioAgentCard> managedCRDs = currentCRDs.stream()
                    .filter(crd -> crd.getMetadata().getAnnotations() != null
                            && MANAGED_ANNOTATION_VALUE.equals(crd.getMetadata().getAnnotations().get(MANAGED_ANNOTATION_KEY)))
                    .collect(Collectors.toList());

            for (ApicurioAgentCard crd : managedCRDs) {
                String name = crd.getMetadata().getName();
                if (!activeK8sNames.contains(name)) {
                    ApicurioAgentCardStatus status = crd.getStatus();
                    if (status == null || !"Stale".equals(status.getSyncStatus())) {
                        log.warn("CRD: {} is no longer active in Apicurio Registry. Marking as Stale.", name);
                        ApicurioAgentCardStatus staleStatus = ApicurioAgentCardStatus.builder()
                                .syncStatus("Stale")
                                .lastSynced(Instant.now().toString())
                                .message("Artifact was deleted or is no longer present in Apicurio Registry")
                                .build();
                        crd.setStatus(staleStatus);
                        kubernetesClient.resources(ApicurioAgentCard.class)
                                .inNamespace(namespace)
                                .resource(crd)
                                .updateStatus(crd);
                        
                        String artifactId = name;
                        if (crd.getMetadata().getAnnotations() != null) {
                            artifactId = crd.getMetadata().getAnnotations().getOrDefault("apicurio.io/artifact-id", name);
                        }
                        apicurioClient.evictCache(artifactId);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to process stale AgentCard CRDs", e);
        }
    }

    private String normalizeK8sName(String artifactId) {
        String normalized = artifactId == null ? "" : artifactId.toLowerCase().replaceAll("[^a-z0-9.-]", "-");
        normalized = normalized.replaceAll("^[^a-z0-9]+", "").replaceAll("[^a-z0-9]+$", "");
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("artifactId cannot be normalized to a valid Kubernetes resource name");
        }
        return normalized.length() > 253 ? normalized.substring(0, 253).replaceAll("[^a-z0-9]+$", "") : normalized;
    }
}
