package io.apicurio.registry.metrics.health.readiness;

import io.apicurio.registry.metrics.health.liveness.PersistenceExceptionLivenessCheck;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.types.Current;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.slf4j.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Default;
import javax.inject.Inject;

/**
 * @author Jakub Senko 'jsenko@redhat.com'
 */
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
     * An exception should also be caught by
     * {@link PersistenceExceptionLivenessCheck}
     */
    private boolean test() {
        try {
            return storage.isReady();
        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }
    }

    @Override
    public synchronized HealthCheckResponse call() {
        return HealthCheckResponse.builder()
            .name("PersistenceSimpleReadinessCheck")
            .status(test())
            .build();
    }
}
