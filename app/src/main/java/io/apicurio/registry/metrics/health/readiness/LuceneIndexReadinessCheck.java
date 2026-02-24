package io.apicurio.registry.metrics.health.readiness;

import io.apicurio.registry.storage.impl.search.LuceneStartupIndexer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

/**
 * Readiness health check that blocks the application from becoming ready until the Lucene
 * startup reindex is complete. When Lucene indexing is disabled, the startup indexer marks
 * itself as ready immediately, so this check does not block startup.
 */
@ApplicationScoped
@Readiness
@Default
public class LuceneIndexReadinessCheck implements HealthCheck {

    @Inject
    LuceneStartupIndexer startupIndexer;

    private boolean test() {
        return startupIndexer.isReady();
    }

    @Override
    public synchronized HealthCheckResponse call() {
        return HealthCheckResponse.builder()
                .name(LuceneIndexReadinessCheck.class.getSimpleName())
                .status(test())
                .build();
    }
}
