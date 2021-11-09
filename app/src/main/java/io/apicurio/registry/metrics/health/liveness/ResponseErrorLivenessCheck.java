package io.apicurio.registry.metrics.health.liveness;

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
 * @author Jakub Senko 'jsenko@redhat.com'
 */
@ApplicationScoped
@Liveness
@Default
public class ResponseErrorLivenessCheck extends AbstractErrorCounterHealthCheck implements HealthCheck, LivenessCheck {

    @Inject
    Logger log;

    @Inject
    RegistryConfigService configService;

    @PostConstruct
    void init() {
        Integer configErrorThreshold = configService.get(RegistryConfigProperty.REGISTRY_METRICS_RESP_ERROR_LIVENESS_ERROR_THRESHOLD, Integer.class);
        Integer configCounterResetWindowDurationSec = configService.get(RegistryConfigProperty.REGISTRY_METRICS_RESP_ERROR_LIVENESS_COUNTER_RESET_WINDOW_DURATION, Integer.class);
        Integer configStatusResetWindowDurationSec = configService.get(RegistryConfigProperty.REGISTRY_METRICS_RESP_ERROR_LIVENESS_STATUS_RESET_WINDOW_DURATION, Integer.class);
        init(configErrorThreshold, configCounterResetWindowDurationSec, configStatusResetWindowDurationSec);
    }

    @Override
    public synchronized HealthCheckResponse call() {
        callSuper();
        return HealthCheckResponse.builder()
                .name("ResponseErrorLivenessCheck")
                .withData("errorCount", errorCounter)
                .up()
                .build();
    }

    @Override
    public void suspect(String reason) {
        boolean disableLogging = configService.get(RegistryConfigProperty.REGISTRY_METRICS_RESP_ERROR_LIVENESS_DISABLE_LOGGING, Boolean.class);
        if (disableLogging != Boolean.TRUE) {
            log.warn("Liveness problem suspected in ResponseErrorLivenessCheck: {}", reason);
        }
        super.suspectSuper();
        if (disableLogging != Boolean.TRUE) {
            Integer configErrorThreshold = configService.get(RegistryConfigProperty.REGISTRY_METRICS_RESP_ERROR_LIVENESS_ERROR_THRESHOLD, Integer.class);
            log.info("After this event, the error counter is {} (out of the maximum {} allowed).", errorCounter, configErrorThreshold);
        }
    }

    @Override
    public void suspectWithException(Throwable reason) {
        boolean disableLogging = configService.get(RegistryConfigProperty.REGISTRY_METRICS_RESP_ERROR_LIVENESS_DISABLE_LOGGING, Boolean.class);
        if (disableLogging != Boolean.TRUE) {
            log.warn("Liveness problem suspected in ResponseErrorLivenessCheck because of an exception: ", reason);
        }
        super.suspectSuper();
        if (disableLogging != Boolean.TRUE) {
            Integer configErrorThreshold = configService.get(RegistryConfigProperty.REGISTRY_METRICS_RESP_ERROR_LIVENESS_ERROR_THRESHOLD, Integer.class);
            log.info("After this event, the error counter is {} (out of the maximum {} allowed).", errorCounter, configErrorThreshold);
        }
    }
}
