package io.apicurio.registry.metrics;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Default;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

/**
 * @author Jakub Senko <jsenko@redhat.com>
 */
@ApplicationScoped
@Liveness
@Default
public class ResponseErrorLivenessCheck extends AbstractErrorCounterHealthCheck implements HealthCheck {

//    private static final Logger log = LoggerFactory.getLogger(ResponseErrorLivenessCheck.class);

    /**
     * Maximum number of HTTP 5xx errors returned to the user
     * as captured by {@link io.apicurio.registry.rest.RegistryExceptionMapper}
     * before the liveness check fails.
     */
    @ConfigProperty(name = "registry.metrics.ResponseErrorLivenessCheck.errorThreshold", defaultValue = "1")
    private Integer configErrorThreshold;

    /**
     * The counter is reset after some time without errors.
     * i.e. to fail the check after 2 errors in a minute, set the threshold to 1 and this configuration option
     * to 60.
     * TODO report the absolute count as a metric?
     */
    @ConfigProperty(name = "registry.metrics.ResponseErrorLivenessCheck.counterResetWindowDurationSec", defaultValue = "60")
    private Integer configCounterResetWindowDurationSec;

    /**
     * If set to a positive value, reset the liveness status after this time window passes without any further errors.
     */
    @ConfigProperty(name = "registry.metrics.ResponseErrorLivenessCheck.statusResetWindowDurationSec", defaultValue = "300")
    private Integer configStatusResetWindowDurationSec;

    @PostConstruct
    void init() {
        init(configErrorThreshold, configCounterResetWindowDurationSec, configStatusResetWindowDurationSec);
    }

    public synchronized void suspect() {
        suspectSuper();
    }

    @Override
    public synchronized HealthCheckResponse call() {
        callSuper();
        return HealthCheckResponse.builder()
                .name("ResponseErrorLivenessCheck")
                .withData("errorCount", errorCounter)
                .state(up)
                .build();
    }
}
