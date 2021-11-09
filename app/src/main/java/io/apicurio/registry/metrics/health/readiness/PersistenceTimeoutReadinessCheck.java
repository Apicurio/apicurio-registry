package io.apicurio.registry.metrics.health.readiness;

import java.time.Duration;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Default;
import javax.inject.Inject;

import io.apicurio.registry.config.RegistryConfigProperty;
import io.apicurio.registry.config.RegistryConfigService;
import io.apicurio.registry.metrics.health.AbstractErrorCounterHealthCheck;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;
import org.slf4j.Logger;

/**
 * Fail readiness check if the duration of processing a artifactStore operation is too high.
 *
 * @author Jakub Senko 'jsenko@redhat.com'
 */
@ApplicationScoped
@Liveness
@Default
public class PersistenceTimeoutReadinessCheck extends AbstractErrorCounterHealthCheck implements HealthCheck {

    @Inject
    Logger log;

    @Inject
    RegistryConfigService configService;

    private Duration timeoutSec;

    @PostConstruct
    void init() {
        Integer configErrorThreshold = configService.get(RegistryConfigProperty.REGISTRY_METRICS_READINESS_ERROR_THRESHOLD, Integer.class);
        Integer configCounterResetWindowDurationSec = configService.get(RegistryConfigProperty.REGISTRY_METRICS_READINESS_COUNTER_RESET_WINDOW_DURATION, Integer.class);
        Integer configStatusResetWindowDurationSec = configService.get(RegistryConfigProperty.REGISTRY_METRICS_READINESS_STATUS_RESET_WINDOW_DURATION, Integer.class);
        Integer configTimeoutSec = configService.get(RegistryConfigProperty.REGISTRY_METRICS_READINESS_TIMEOUT, Integer.class);
        init(configErrorThreshold, configCounterResetWindowDurationSec, configStatusResetWindowDurationSec);
        timeoutSec = Duration.ofSeconds(configTimeoutSec);
    }

    @Override
    public synchronized HealthCheckResponse call() {
        callSuper();
        return HealthCheckResponse.builder()
                .name("PersistenceTimeoutReadinessCheck")
                .withData("errorCount", errorCounter)
                .up()
                .build();
    }

    public Duration getTimeoutSec() {
        return timeoutSec;
    }

    public void suspect() {
        this.suspectSuper();
    }
}
