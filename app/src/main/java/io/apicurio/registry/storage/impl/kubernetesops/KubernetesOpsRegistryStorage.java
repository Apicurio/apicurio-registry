package io.apicurio.registry.storage.impl.kubernetesops;

import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.StorageMetricsApply;
import io.apicurio.registry.storage.impl.polling.AbstractPollingRegistryStorage;
import io.apicurio.registry.storage.impl.polling.DataSourceManager;
import io.quarkus.arc.lookup.LookupIfProperty;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

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
    KubernetesManager kubernetesManager;

    @ConfigProperty(name = "apicurio.storage.kind")
    @Info(category = CATEGORY_STORAGE, description = "Application storage variant, for example, sql, kafkasql, gitops, or kubernetesops", availableSince = "3.0.0")
    String registryStorageType;

    @Override
    protected DataSourceManager getDataSourceManager() {
        return kubernetesManager;
    }

    @Override
    public String storageName() {
        return "kubernetesops";
    }

    @Scheduled(concurrentExecution = SKIP, every = "${apicurio.kubernetesops.refresh.every:30s}")
    void scheduledRefresh() {
        if ("kubernetesops".equals(registryStorageType)) {
            refresh();
        }
    }

    @PreDestroy
    void onDestroy() {
    }
}
