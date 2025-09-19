package io.apicurio.registry.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.Sample;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Inject;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.regex.Pattern;

import static io.apicurio.registry.metrics.MetricsConstants.REST_REQUESTS;
import static io.apicurio.registry.metrics.MetricsConstants.REST_REQUESTS_DESCRIPTION;
import static io.apicurio.registry.metrics.MetricsConstants.REST_REQUESTS_TAG_METHOD;
import static io.apicurio.registry.metrics.MetricsConstants.REST_REQUESTS_TAG_PATH;
import static io.apicurio.registry.metrics.MetricsConstants.REST_REQUESTS_TAG_STATUS_CODE_GROUP;
import static java.lang.Boolean.TRUE;
import static java.lang.String.format;

/**
 * Filters REST API requests and responses to report metrics about them.
 */
@Provider
@Default
@ApplicationScoped
public class RestMetricsResponseFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final String TIMER_SAMPLE_CONTEXT_PROPERTY_NAME = "request-timer-sample";
    private static final String SKIP_TIMER_SAMPLE_CONTEXT_PROPERTY_NAME = "skip-request-timer-sample";

    private static final Pattern ENABLED_PATTERN = Pattern.compile("/apis/.*");

    @Context
    ResourceInfo resourceInfo;

    @Inject
    MeterRegistry registry;

    @Inject
    Logger log;

    @PostConstruct
    void initializeCounters() {
        // See https://github.com/Apicurio/apicurio-registry/issues/6296
        for (int statusCode = 100; statusCode < 501; statusCode += 100) {
            String statusCodeGroup = getStatusCodeGroup(statusCode);
            Timer.builder(REST_REQUESTS)
                    .description(REST_REQUESTS_DESCRIPTION)
                    // NOTE: We have to include the empty tag values,
                    // otherwise Micrometer will not create timers for the actual requests.
                    // TODO: Create Micrometer issue?
                    .tag(REST_REQUESTS_TAG_PATH, "")
                    .tag(REST_REQUESTS_TAG_METHOD, "")
                    .tag(REST_REQUESTS_TAG_STATUS_CODE_GROUP, statusCodeGroup)
                    .register(registry);
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {

        final var enabled = ENABLED_PATTERN.matcher(requestContext.getUriInfo().getPath()).matches();

        if (enabled) {
            requestContext.setProperty(TIMER_SAMPLE_CONTEXT_PROPERTY_NAME, Timer.start(registry));
        } else {
            requestContext.setProperty(SKIP_TIMER_SAMPLE_CONTEXT_PROPERTY_NAME, true);
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {

        if (requestContext.getProperty(TIMER_SAMPLE_CONTEXT_PROPERTY_NAME) != null) {

            var timer = Timer.builder(REST_REQUESTS)
                    .description(REST_REQUESTS_DESCRIPTION)
                    .tag(REST_REQUESTS_TAG_PATH, getPath())
                    .tag(REST_REQUESTS_TAG_METHOD, requestContext.getMethod())
                    .tag(REST_REQUESTS_TAG_STATUS_CODE_GROUP, getStatusCodeGroup(responseContext.getStatus()))
                    .register(registry);

            var sample = (Sample) requestContext.getProperty(TIMER_SAMPLE_CONTEXT_PROPERTY_NAME);
            sample.stop(timer);

        } else {

            if (!TRUE.equals(requestContext.getProperty(SKIP_TIMER_SAMPLE_CONTEXT_PROPERTY_NAME))) {
                // TODO: Use RuntimeAssertionFailedException?
                log.warn("Could not find timer sample in the context of request: {} {}",
                        requestContext.getMethod(),
                        requestContext.getUriInfo().getPath());
            }
        }
    }

    private String getStatusCodeGroup(int statusCode) {
        if (statusCode < 100 || statusCode >= 600) {
            return "";
        }
        return format("%dxx", statusCode / 100);
    }

    private String getPath() {
        return getResourceClassPath() + getResourceMethodPath();
    }

    private String getResourceClassPath() {
        Path classPath = resourceInfo.getResourceClass().getAnnotation(Path.class);
        return classPath != null ? classPath.value() : "";
    }

    private String getResourceMethodPath() {
        Path methodPath = resourceInfo.getResourceMethod().getAnnotation(Path.class);
        return methodPath != null ? methodPath.value() : "";
    }
}
