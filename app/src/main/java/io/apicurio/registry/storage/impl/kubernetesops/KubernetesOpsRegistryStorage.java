package io.apicurio.registry.storage.impl.kubernetesops;

import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.StorageMetricsApply;
import io.apicurio.registry.storage.impl.polling.AbstractPollingRegistryStorage;
import io.apicurio.registry.storage.impl.polling.DataSourceManager;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.arc.lookup.LookupIfProperty;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
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
public class KubernetesOpsRegistryStorage extends AbstractPollingRegistryStorage {

    @Inject
    Logger log;

    @Inject
    KubernetesManager kubernetesManager;

    @Inject
    KubernetesOpsConfigProperties config;

    @ConfigProperty(name = "apicurio.storage.kind")
    @Info(category = CATEGORY_STORAGE, description = "Application storage variant, for example, sql, kafkasql, gitops, or kubernetesops", availableSince = "3.0.0")
    String registryStorageType;

    private volatile long lastRefreshTime = 0;
    private static final long MIN_REFRESH_INTERVAL_MS = 1000; // 1 second debounce

    @Override
    protected DataSourceManager getDataSourceManager() {
        return kubernetesManager;
    }

    @Override
    public String storageName() {
        return "kubernetesops";
    }

    @Override
    public void initialize() {
        super.initialize();

        kubernetesManager.setRefreshCallback(this::triggerRefreshFromWatch);

        if (config.isWatchEnabled()) {
            kubernetesManager.startWatch();
        }
    }

    private void triggerRefreshFromWatch() {
        long now = System.currentTimeMillis();
        if (now - lastRefreshTime < MIN_REFRESH_INTERVAL_MS) {
            log.debug("Skipping refresh, too soon since last refresh");
            return;
        }

        log.debug("Watch triggered refresh");

        try {
            // Activate request context since the watch callback runs in a background thread
            ManagedContext requestContext = Arc.container().requestContext();
            if (requestContext.isActive()) {
                refresh();
            } else {
                requestContext.activate();
                try {
                    refresh();
                } finally {
                    requestContext.terminate();
                }
            }
        } catch (Exception e) {
            log.warn("Watch-triggered refresh failed: {}", e.getMessage());
        } finally {
            lastRefreshTime = System.currentTimeMillis();
        }
    }

    @Scheduled(concurrentExecution = SKIP, every = "${apicurio.kubernetesops.refresh.every:30s}")
    void scheduledRefresh() {
        if (!"kubernetesops".equals(registryStorageType)) {
            return;
        }

        // Always run scheduled polling as the primary fallback.
        // The watch provides real-time updates when available,
        // but scheduled polling ensures we don't miss changes.
        lastRefreshTime = System.currentTimeMillis();
        refresh();
    }

    @PreDestroy
    void onDestroy() {
        kubernetesManager.stopWatch();
    }
}
