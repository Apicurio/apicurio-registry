package io.apicurio.registry.metrics;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Default;

/**
 * Fail liveness check if the number of exceptions thrown by storage is too high.
 *
 * @author Jakub Senko <jsenko@redhat.com>
 */
@ApplicationScoped
@Liveness
@Default
public class PersistenceExceptionLivenessCheck extends AbstractErrorCounterHealthCheck implements HealthCheck {

    private static final Logger log = LoggerFactory.getLogger(PersistenceExceptionLivenessCheck.class);

    /**
     * Maximum number of exceptions raised by storage implementation,
     * as captured by this interceptor,
     * before the liveness check fails.
     */
    @ConfigProperty(name = "registry.metrics.PersistenceExceptionLivenessCheck.errorThreshold", defaultValue = "1")
    private Integer configErrorThreshold;

    /**
     * The counter is reset after some time without errors.
     * i.e. to fail the check after 2 errors in a minute, set the threshold to 1 and this configuration option
     * to 60.
     * TODO report the absolute count as a metric?
     */
    @ConfigProperty(name = "registry.metrics.PersistenceExceptionLivenessCheck.counterResetWindowDurationSec", defaultValue = "60")
    private Integer configCounterResetWindowDurationSec;

    /**
     * If set to a positive value, reset the liveness status after this time window passes without any further errors.
     */
    @ConfigProperty(name = "registry.metrics.PersistenceExceptionLivenessCheck.statusResetWindowDurationSec", defaultValue = "300")
    private Integer configStatusResetWindowDurationSec;

    @PostConstruct
    void init() {
        init(configErrorThreshold, configCounterResetWindowDurationSec, configStatusResetWindowDurationSec);
    }

    @Override
    public synchronized HealthCheckResponse call() {
        callSuper();
        return HealthCheckResponse.builder()
                .name("PersistenceExceptionLivenessCheck")
                .withData("errorCount", errorCounter)
                .state(up)
                .build();
    }
}
