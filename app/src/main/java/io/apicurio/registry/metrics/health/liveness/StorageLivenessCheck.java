package io.apicurio.registry.metrics.health.liveness;

import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.types.Current;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Inject;

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
                                  .status(storage.isAlive())
                                  .build();
    }
}
