package io.apicurio.registry.metrics.health.readiness;

import io.apicurio.registry.ImportLifecycleBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

@ApplicationScoped
@Readiness
@Default
public class ImportLifecycleReadinessCheck implements HealthCheck {

    @Inject
    ImportLifecycleBean importLifecycleBean;

    private boolean test() {
        return importLifecycleBean.isReady();
    }

    @Override
    public synchronized HealthCheckResponse call() {
        return HealthCheckResponse.builder().name(ImportLifecycleReadinessCheck.class.getSimpleName()).status(test()).build();
    }
}
