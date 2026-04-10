package io.apicurio.registry.storage.impl.kubernetesops;

import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.StorageMetricsApply;
import io.apicurio.registry.storage.StorageEvent;
import io.apicurio.registry.storage.StorageEventType;
import io.apicurio.registry.storage.impl.polling.AbstractPollingRegistryStorage;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.arc.lookup.LookupIfProperty;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_STORAGE;
import static io.quarkus.scheduler.Scheduled.ConcurrentExecution.SKIP;

/**
 * KubernetesOps storage implementation that loads registry data from Kubernetes ConfigMaps.
 * This provides a Kubernetes-native approach to managing registry artifacts using standard
 * Kubernetes tooling (kubectl, ArgoCD, Flux, etc.).
 */
@ApplicationScoped
@StorageMetricsApply
@Logged
@LookupIfProperty(name = "apicurio.storage.kind", stringValue = "kubernetesops")
public class KubernetesOpsRegistryStorage extends AbstractPollingRegistryStorage<String> {

    @Inject
    Logger log;

    @Inject
    KubernetesManager kubernetesManager;

    @Inject
    KubernetesOpsConfig config;

    @Inject
    Event<StorageEvent> storageEvent;

    @ConfigProperty(name = "apicurio.storage.kind")
    @Info(category = CATEGORY_STORAGE, description = "Application storage variant, for example, sql, kafkasql, gitops, or kubernetesops", availableSince = "3.0.0")
    String registryStorageType;

    @Override
    public void initialize() {
        super.initialize(config, kubernetesManager);

        if (config.isWatchEnabled()) {
            kubernetesManager.setRefreshCallback(this::triggerRefreshFromWatch);
            kubernetesManager.startWatch();
        }

        storageEvent.fireAsync(StorageEvent.builder().type(StorageEventType.READY).build());
    }

    private void triggerRefreshFromWatch() {
        log.debug("Watch triggered refresh");

        // Always set the pending flag so that if the lock is held,
        // the next refresh cycle will re-poll immediately
        onWatchEvent();

        try {
            // Activate request context since the watch callback runs in a background thread
            ManagedContext requestContext = Arc.container().requestContext();
            if (requestContext.isActive()) {
                tryRefresh();
            } else {
                requestContext.activate();
                try {
                    tryRefresh();
                } finally {
                    requestContext.terminate();
                }
            }
        } catch (Exception e) {
            log.warn("Watch-triggered refresh failed: {}", e.getMessage());
        }
    }

    @Scheduled(concurrentExecution = SKIP, every = "${apicurio.polling-storage.try-refresh.every:2.5s}")
    void scheduledRefresh() {
        if ("kubernetesops".equals(registryStorageType)) {
            tryRefresh();
        }
    }
}
