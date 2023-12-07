package io.apicurio.registry.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

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
import java.io.IOException;
import java.util.regex.Pattern;

import static io.apicurio.registry.metrics.MetricsConstants.REST_REQUESTS;
import static io.apicurio.registry.metrics.MetricsConstants.REST_REQUESTS_COUNTER;
import static io.apicurio.registry.metrics.MetricsConstants.REST_REQUESTS_COUNTER_DESCRIPTION;
import static io.apicurio.registry.metrics.MetricsConstants.REST_REQUESTS_DESCRIPTION;
import static io.apicurio.registry.metrics.MetricsConstants.REST_REQUESTS_TAG_METHOD;
import static io.apicurio.registry.metrics.MetricsConstants.REST_REQUESTS_TAG_PATH;
import static io.apicurio.registry.metrics.MetricsConstants.REST_REQUESTS_TAG_STATUS_CODE_FAMILY;

/**
 * Filters REST API requests and responses to report metrics
 * about them.
 *
 */
@Provider
@Default
@ApplicationScoped
public class RestMetricsResponseFilter implements ContainerRequestFilter, ContainerResponseFilter {

    @Inject
    MeterRegistry registry;

    public static final String TIMER_SAMPLE_CONTEXT_PROPERTY_NAME = "request-timer-sample";

    @Context
    private ResourceInfo resourceInfo;

    // I couldn't figure out an easy way to use an annotation that can be applied on the whole REST resource class,
    // instead of on each method (or jakarta.ws.rs.core.Application).
    // See https://docs.oracle.com/javaee/7/api/javax/ws/rs/NameBinding.html
    static final Pattern ENABLED_PATTERN = Pattern.compile("/apis/.*");

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        final boolean enabled = ENABLED_PATTERN.matcher(requestContext.getUriInfo().getPath()).matches();
        if (enabled) {
            Timer.Sample sample = Timer.start(registry);
            requestContext.setProperty(TIMER_SAMPLE_CONTEXT_PROPERTY_NAME, sample);
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
            throws IOException {

        if (requestContext.getProperty(TIMER_SAMPLE_CONTEXT_PROPERTY_NAME) == null) {
            return;
        }

        Timer timer = Timer
                .builder(REST_REQUESTS)
                .description(REST_REQUESTS_DESCRIPTION)
                .tag(REST_REQUESTS_TAG_PATH, this.getPath())
                .tag(REST_REQUESTS_TAG_METHOD, requestContext.getMethod())
                .tag(REST_REQUESTS_TAG_STATUS_CODE_FAMILY, this.getStatusGroup(responseContext.getStatus()))
                .register(registry);

        Timer.Sample sample = (Timer.Sample) requestContext.getProperty(TIMER_SAMPLE_CONTEXT_PROPERTY_NAME);
        sample.stop(timer);

        Counter.builder(REST_REQUESTS_COUNTER)
                .description(REST_REQUESTS_COUNTER_DESCRIPTION)
                .tag(REST_REQUESTS_TAG_PATH, this.getPath())
                .tag(REST_REQUESTS_TAG_METHOD, requestContext.getMethod())
                .tag(REST_REQUESTS_TAG_STATUS_CODE_FAMILY, this.getStatusGroup(responseContext.getStatus()))
                .register(registry)
                .increment();
    }

    private String getStatusGroup(int statusCode) {
        if (statusCode < 100 || statusCode >= 600) {
            return "";
        }
        int statusCodeGroup = statusCode / 100;
        return String.format("%dxx", statusCodeGroup);
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
