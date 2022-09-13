package io.apicurio.registry.metrics.health.readiness;

import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.metrics.health.AbstractErrorCounterHealthCheck;
import org.eclipse.microprofile.config.inject.ConfigProperty;
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
import javax.enterprise.inject.Instance;
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

    /**
     * Maximum number of requests taking more than {@link ResponseTimeoutReadinessCheck#configTimeoutSec} seconds,
     * before the readiness check fails.
     */
    @ConfigProperty(name = "registry.metrics.ResponseTimeoutReadinessCheck.errorThreshold", defaultValue = "1")
    @Info(category = "health", description = "Error threshold of response readiness check", availableSince = "1.0.2.Final")
    Instance<Integer> configErrorThreshold;

    /**
     * The counter is reset after some time without errors.
     * i.e. to fail the check after 2 errors in a minute, set the threshold to 1 and this configuration option
     * to 60.
     * TODO report the absolute count as a metric?
     */
    @ConfigProperty(name = "registry.metrics.ResponseTimeoutReadinessCheck.counterResetWindowDurationSec", defaultValue = "60")
    @Info(category = "health", description = "Counter reset window duration of response readiness check", availableSince = "1.0.2.Final")
    Instance<Integer> configCounterResetWindowDurationSec;

    /**
     * If set to a positive value, reset the readiness status after this time window passes without any further errors.
     */
    @ConfigProperty(name = "registry.metrics.ResponseTimeoutReadinessCheck.statusResetWindowDurationSec", defaultValue = "300")
    @Info(category = "health", description = "Status reset window duration of response readiness check", availableSince = "1.0.2.Final")
    Instance<Integer> configStatusResetWindowDurationSec;

    /**
     * Set the request duration in seconds, after which it's considered an error.
     * TODO This may be expected on some endpoints. Add a way to ignore those.
     */
    @ConfigProperty(name = "registry.metrics.ResponseTimeoutReadinessCheck.timeoutSec", defaultValue = "10")
    @Info(category = "health", description = "Timeout of response readiness check", availableSince = "1.0.2.Final")
    Instance<Integer> configTimeoutSec;

    private Duration timeoutSec;

    @PostConstruct
    void init() {
        init(configErrorThreshold.get(), configCounterResetWindowDurationSec.get(), configStatusResetWindowDurationSec.get());
        timeoutSec = Duration.ofSeconds(configTimeoutSec.get());
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

        } else if (responseContext.getStatus() != 404){
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
