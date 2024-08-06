package io.apicurio.registry.metrics.health.readiness;

import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.metrics.health.AbstractErrorCounterHealthCheck;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;
import org.slf4j.Logger;

import java.time.Duration;

/**
 * Fail readiness check if the duration of processing a artifactStore operation is too high.
 */
@ApplicationScoped
@Liveness
@Default
public class PersistenceTimeoutReadinessCheck extends AbstractErrorCounterHealthCheck implements HealthCheck {

    @Inject
    Logger log;

    /**
     * Maximum number of timeouts as captured by this interceptor, before the readiness check fails.
     */
    @ConfigProperty(name = "apicurio.metrics.PersistenceTimeoutReadinessCheck.errorThreshold", defaultValue = "5")
    @Info(category = "health", description = "Error threshold of persistence readiness check", availableSince = "1.0.2.Final")
    Integer configErrorThreshold;

    /**
     * The counter is reset after some time without errors. i.e. to fail the check after 2 errors in a minute,
     * set the threshold to 1 and this configuration option to 60. TODO report the absolute count as a metric?
     */
    @ConfigProperty(name = "apicurio.metrics.PersistenceTimeoutReadinessCheck.counterResetWindowDuration.seconds", defaultValue = "60")
    @Info(category = "health", description = "Counter reset window duration of persistence readiness check", availableSince = "1.0.2.Final")
    Integer configCounterResetWindowDurationSec;

    /**
     * If set to a positive value, reset the readiness status after this time window passes without any
     * further errors.
     */
    @ConfigProperty(name = "apicurio.metrics.PersistenceTimeoutReadinessCheck.statusResetWindowDuration.seconds", defaultValue = "300")
    @Info(category = "health", description = "Status reset window duration of persistence readiness check", availableSince = "1.0.2.Final")
    Integer configStatusResetWindowDurationSec;

    /**
     * Set the operation duration in seconds, after which it's considered an error.
     */
    @ConfigProperty(name = "apicurio.metrics.PersistenceTimeoutReadinessCheck.timeout.seconds", defaultValue = "15")
    @Info(category = "health", description = "Timeout of persistence readiness check", availableSince = "1.0.2.Final")
    Integer configTimeoutSec;

    private Duration timeoutSec;

    @PostConstruct
    void init() {
        init(configErrorThreshold, configCounterResetWindowDurationSec, configStatusResetWindowDurationSec);
        timeoutSec = Duration.ofSeconds(configTimeoutSec);
    }

    @Override
    public synchronized HealthCheckResponse call() {
        callSuper();
        return HealthCheckResponse.builder().name("PersistenceTimeoutReadinessCheck")
                .withData("errorCount", errorCounter).status(up).build();
    }

    public Duration getTimeoutSec() {
        return timeoutSec;
    }

    public void suspect() {
        this.suspectSuper();
    }
}
