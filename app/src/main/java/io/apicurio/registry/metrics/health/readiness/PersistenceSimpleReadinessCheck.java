package io.apicurio.registry.metrics.health.readiness;

import io.apicurio.registry.metrics.health.liveness.PersistenceExceptionLivenessCheck;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.types.Current;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.slf4j.Logger;

@ApplicationScoped
@Readiness
@Default
public class PersistenceSimpleReadinessCheck implements HealthCheck {

    @Inject
    Logger log;

    @Inject
    @Current
    RegistryStorage storage;

    /**
     * An exception should also be caught by {@link PersistenceExceptionLivenessCheck}
     */
    private boolean test() {
        try {
            return storage.isReady();
        } catch (Exception ex) {
            log.warn("Persistence is not ready:", ex);
            return false;
        }
    }

    @Override
    public synchronized HealthCheckResponse call() {
        return HealthCheckResponse.builder().name("PersistenceSimpleReadinessCheck").status(test()).build();
    }
}
