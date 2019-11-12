package io.apicurio.registry.metrics;

import java.util.List;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Default;
import javax.inject.Inject;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.types.RuleType;

/**
 * @author Jakub Senko <jsenko@redhat.com>
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
            List<RuleType> res = storage.getGlobalRules();
            if (res == null) {
                return false;
            }
            return true;
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
