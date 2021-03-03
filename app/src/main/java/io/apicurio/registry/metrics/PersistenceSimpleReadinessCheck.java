package io.apicurio.registry.metrics;

import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.types.Current;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

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

//    private static final Logger log = LoggerFactory.getLogger(PersistenceSimpleReadinessCheck.class);

    @Inject
    @Current
    RegistryStorage storage;

    /**
     * An exception should also be caught by
     * {@link io.apicurio.registry.metrics.PersistenceExceptionLivenessCheck}
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
                .state(test())
                .build();
    }
}
