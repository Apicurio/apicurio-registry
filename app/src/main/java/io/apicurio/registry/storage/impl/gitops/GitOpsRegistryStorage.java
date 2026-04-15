package io.apicurio.registry.storage.impl.gitops;

import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.StorageMetricsApply;
import io.apicurio.registry.storage.StorageEvent;
import io.apicurio.registry.storage.StorageEventType;
import io.apicurio.registry.storage.impl.polling.AbstractPollingRegistryStorage;
import io.quarkus.arc.lookup.LookupIfProperty;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_STORAGE;
import static io.quarkus.scheduler.Scheduled.ConcurrentExecution.SKIP;

@ApplicationScoped
@StorageMetricsApply
@Logged
@LookupIfProperty(name = "apicurio.storage.kind", stringValue = "gitops")
public class GitOpsRegistryStorage extends AbstractPollingRegistryStorage<GitOpsMarker> {

    @ConfigProperty(name = "apicurio.storage.kind")
    @Info(category = CATEGORY_STORAGE, description = "Application storage variant, for example, sql, kafkasql, or gitops", availableSince = "3.0.0")
    String registryStorageType;

    @Inject
    GitManager gitManager;

    @Inject
    GitOpsConfig config;

    @Inject
    Event<StorageEvent> storageEvent;

    @Override
    public void initialize() {
        super.initialize(config, gitManager);
        storageEvent.fireAsync(StorageEvent.builder().type(StorageEventType.READY).build());
    }

    @Override
    protected Map<String, String> markerToSources(GitOpsMarker marker) {
        if (marker == null) {
            return null;
        }
        var result = new java.util.LinkedHashMap<String, String>();
        marker.getCommits().forEach((id, commit) ->
                result.put(id, commit != null ? commit.name() : null));
        return result;
    }

    @Scheduled(concurrentExecution = SKIP, every = "${apicurio.polling-storage.try-refresh.every:2.5s}")
    void scheduledRefresh() {
        if ("gitops".equals(registryStorageType)) {
            tryRefresh();
        }
    }
}
