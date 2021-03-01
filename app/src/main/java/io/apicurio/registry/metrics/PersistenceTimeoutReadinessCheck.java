package io.apicurio.registry.metrics;

import java.time.Duration;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Default;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

/**
 * Fail readiness check if the duration of processing a artifactStore operation is too high.
 *
 * @author Jakub Senko 'jsenko@redhat.com'
 */
@ApplicationScoped
@Liveness
@Default
public class PersistenceTimeoutReadinessCheck extends AbstractErrorCounterHealthCheck implements HealthCheck {

//    private static final Logger log = LoggerFactory.getLogger(PersistenceTimeoutReadinessCheck.class);

    /**
     * Maximum number of timeouts as captured by this interceptor,
     * before the readiness check fails.
     */
    @ConfigProperty(name = "registry.metrics.PersistenceTimeoutReadinessCheck.errorThreshold", defaultValue = "5")
    Integer configErrorThreshold;

    /**
     * The counter is reset after some time without errors.
     * i.e. to fail the check after 2 errors in a minute, set the threshold to 1 and this configuration option
     * to 60.
     * TODO report the absolute count as a metric?
     */
    @ConfigProperty(name = "registry.metrics.PersistenceTimeoutReadinessCheck.counterResetWindowDurationSec", defaultValue = "60")
    Integer configCounterResetWindowDurationSec;

    /**
     * If set to a positive value, reset the readiness status after this time window passes without any further errors.
     */
    @ConfigProperty(name = "registry.metrics.PersistenceTimeoutReadinessCheck.statusResetWindowDurationSec", defaultValue = "300")
    Integer configStatusResetWindowDurationSec;

    /**
     * Set the operation duration in seconds, after which it's considered an error.
     */
    @ConfigProperty(name = "registry.metrics.PersistenceTimeoutReadinessCheck.timeoutSec", defaultValue = "15")
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
                .state(up)
                .build();
    }

    public Duration getTimeoutSec() {
        return timeoutSec;
    }
}
