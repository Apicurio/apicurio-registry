package io.apicurio.registry.storage.impl.gitops;

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

@ApplicationScoped
@StorageMetricsApply
@Logged
@LookupIfProperty(name = "apicurio.storage.kind", stringValue = "gitops")
public class GitOpsRegistryStorage extends AbstractPollingRegistryStorage {

    @Inject
    GitManager gitManager;

    @ConfigProperty(name = "apicurio.storage.kind")
    @Info(category = CATEGORY_STORAGE, description = "Application storage variant, for example, sql, kafkasql, or gitops", availableSince = "3.0.0")
    String registryStorageType;

    @Override
    protected DataSourceManager getDataSourceManager() {
        return gitManager;
    }

    @Override
    public String storageName() {
        return "gitops";
    }

    @Scheduled(concurrentExecution = SKIP, every = "{apicurio.gitops.refresh.every}")
    void scheduledRefresh() {
        if ("gitops".equals(registryStorageType)) {
            refresh();
        }
    }

    @PreDestroy
    void onDestroy() {
    }
}
