package io.apicurio.registry.metrics.health.readiness;

import io.apicurio.registry.storage.impl.search.ElasticsearchStartupIndexer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

/**
 * Readiness health check that blocks the application from becoming ready until the
 * Elasticsearch startup reindex is complete. When Elasticsearch indexing is disabled,
 * the startup indexer marks itself as ready immediately, so this check does not block
 * startup.
 */
@ApplicationScoped
@Readiness
@Default
public class ElasticsearchIndexReadinessCheck implements HealthCheck {

    @Inject
    ElasticsearchStartupIndexer startupIndexer;

    private boolean test() {
        return startupIndexer.isReady();
    }

    @Override
    public synchronized HealthCheckResponse call() {
        return HealthCheckResponse.builder()
                .name(ElasticsearchIndexReadinessCheck.class.getSimpleName())
                .status(test())
                .build();
    }
}
