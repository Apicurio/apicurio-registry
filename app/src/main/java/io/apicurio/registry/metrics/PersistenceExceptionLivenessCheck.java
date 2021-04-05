package io.apicurio.registry.metrics;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Default;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fail liveness check if the number of exceptions thrown by artifactStore is too high.
 *
 * @author Jakub Senko 'jsenko@redhat.com'
 */
@ApplicationScoped
@Liveness
@Default
public class PersistenceExceptionLivenessCheck extends AbstractErrorCounterHealthCheck implements HealthCheck, LivenessCheck {

    private static final Logger log = LoggerFactory.getLogger(PersistenceExceptionLivenessCheck.class);

    /**
     * Maximum number of exceptions raised by artifactStore implementation,
     * as captured by this interceptor,
     * before the liveness check fails.
     */
    @ConfigProperty(name = "registry.metrics.PersistenceExceptionLivenessCheck.errorThreshold", defaultValue = "1")
    Integer configErrorThreshold;

    /**
     * The counter is reset after some time without errors.
     * i.e. to fail the check after 2 errors in a minute, set the threshold to 1 and this configuration option
     * to 60.
     * TODO report the absolute count as a metric?
     */
    @ConfigProperty(name = "registry.metrics.PersistenceExceptionLivenessCheck.counterResetWindowDurationSec", defaultValue = "60")
    Integer configCounterResetWindowDurationSec;

    /**
     * If set to a positive value, reset the liveness status after this time window passes without any further errors.
     */
    @ConfigProperty(name = "registry.metrics.PersistenceExceptionLivenessCheck.statusResetWindowDurationSec", defaultValue = "300")
    Integer configStatusResetWindowDurationSec;

    @ConfigProperty(name = "registry.metrics.PersistenceExceptionLivenessCheck.disableLogging", defaultValue = "false")
    Boolean disableLogging;

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

    @Override
    public void suspect(String reason) {
        if (disableLogging != Boolean.TRUE) {
            log.warn("Liveness problem suspected in PersistenceExceptionLivenessCheck: {}", reason);
        }
        super.suspectSuper();
        if (disableLogging != Boolean.TRUE) {
            log.info("After this event, the error counter is {} (out of the maximum {} allowed).", errorCounter, configErrorThreshold);
        }
    }

    @Override
    public void suspectWithException(Throwable reason) {
        if (disableLogging != Boolean.TRUE) {
            log.warn("Liveness problem suspected in PersistenceExceptionLivenessCheck because of an exception: ", reason);
        }
        super.suspectSuper();
        if (disableLogging != Boolean.TRUE) {
            log.info("After this event, the error counter is {} (out of the maximum {} allowed).", errorCounter, configErrorThreshold);
        }
    }
}
