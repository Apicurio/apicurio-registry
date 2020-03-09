package io.apicurio.registry.metrics;

import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.types.Current;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Default;
import javax.inject.Inject;

/**
 * @author Ales Justin
 */
@ApplicationScoped
@Liveness
@Default
public class StorageLivenessCheck implements HealthCheck {

    @Inject
    @Current
    RegistryStorage storage;

    @Override
    public synchronized HealthCheckResponse call() {
        return HealthCheckResponse.builder()
                                  .name("StorageLivenessCheck")
                                  .state(storage.isAlive())
                                  .build();
    }
}
