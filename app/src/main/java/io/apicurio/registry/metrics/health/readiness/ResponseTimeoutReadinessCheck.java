package io.apicurio.registry.metrics.health.readiness;

import io.apicurio.registry.config.RegistryConfigProperty;
import io.apicurio.registry.config.RegistryConfigService;
import io.apicurio.registry.metrics.health.AbstractErrorCounterHealthCheck;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.slf4j.Logger;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Default;
import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.ext.Provider;

/**
 * @author Jakub Senko 'jsenko@redhat.com'
 */
@ApplicationScoped
@Readiness
@Default
@Provider
public class ResponseTimeoutReadinessCheck extends AbstractErrorCounterHealthCheck
        implements HealthCheck, ContainerRequestFilter, ContainerResponseFilter {

    private static final String HEADER_NAME = "X-Apicurio-Registry-ResponseTimeoutReadinessCheck-RequestStart";

    @Inject
    Logger log;

    @Inject
    RegistryConfigService configService;

    private Duration timeoutSec;

    @PostConstruct
    void init() {
        Integer configErrorThreshold = configService.get(RegistryConfigProperty.REGISTRY_METRICS_RESP_TIMEOUT_READINESS_ERROR_THRESHOLD, Integer.class);
        Integer configCounterResetWindowDurationSec = configService.get(RegistryConfigProperty.REGISTRY_METRICS_RESP_TIMEOUT_READINESS_COUNTER_RESET_WINDOW_DURATION, Integer.class);
        Integer configStatusResetWindowDurationSec = configService.get(RegistryConfigProperty.REGISTRY_METRICS_RESP_TIMEOUT_READINESS_STATUS_RESET_WINDOW_DURATION, Integer.class);
        Integer configTimeoutSec = configService.get(RegistryConfigProperty.REGISTRY_METRICS_RESP_TIMEOUT_READINESS_TIMEOUT, Integer.class);
        init(configErrorThreshold, configCounterResetWindowDurationSec, configStatusResetWindowDurationSec);
        timeoutSec = Duration.ofSeconds(configTimeoutSec);
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        requestContext.getHeaders().putSingle(HEADER_NAME, Instant.now().toString());
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        String requestStart = requestContext.getHeaderString(HEADER_NAME);
        if (requestStart != null) {
            try {
                if (Instant.parse(requestStart).plus(timeoutSec).isBefore(Instant.now())) {
                    suspectSuper();
                }
            } catch (DateTimeParseException ex) {
                log.error("Value '{}' of header '{}' is the wrong format!", requestStart, HEADER_NAME);
            }

        } else {
            log.warn("Expected header '{}' not found.", HEADER_NAME);
        }
    }

    @Override
    public synchronized HealthCheckResponse call() {
        callSuper();
        return HealthCheckResponse.builder()
                .name("ResponseTimeoutReadinessCheck")
                .withData("errorCount", errorCounter)
                .up()
                .build();
    }
}
