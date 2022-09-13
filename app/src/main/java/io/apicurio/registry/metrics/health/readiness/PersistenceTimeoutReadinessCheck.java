package io.apicurio.registry.metrics.health.readiness;

import java.time.Duration;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Default;
import javax.inject.Inject;

import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.metrics.health.AbstractErrorCounterHealthCheck;
import org.eclipse.microprofile.config.inject.ConfigProperty;
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

    /**
     * Maximum number of timeouts as captured by this interceptor,
     * before the readiness check fails.
     */
    @ConfigProperty(name = "registry.metrics.PersistenceTimeoutReadinessCheck.errorThreshold", defaultValue = "5")
    @Info(category = "health", description = "Error threshold of persistence readiness check", availableSince = "1.0.2.Final")
    Integer configErrorThreshold;

    /**
     * The counter is reset after some time without errors.
     * i.e. to fail the check after 2 errors in a minute, set the threshold to 1 and this configuration option
     * to 60.
     * TODO report the absolute count as a metric?
     */
    @ConfigProperty(name = "registry.metrics.PersistenceTimeoutReadinessCheck.counterResetWindowDurationSec", defaultValue = "60")
    @Info(category = "health", description = "Counter reset window duration of persistence readiness check", availableSince = "1.0.2.Final")
    Integer configCounterResetWindowDurationSec;

    /**
     * If set to a positive value, reset the readiness status after this time window passes without any further errors.
     */
    @ConfigProperty(name = "registry.metrics.PersistenceTimeoutReadinessCheck.statusResetWindowDurationSec", defaultValue = "300")
    @Info(category = "health", description = "Status reset window duration of persistence readiness check", availableSince = "1.0.2.Final")
    Integer configStatusResetWindowDurationSec;

    /**
     * Set the operation duration in seconds, after which it's considered an error.
     */
    @ConfigProperty(name = "registry.metrics.PersistenceTimeoutReadinessCheck.timeoutSec", defaultValue = "15")
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
