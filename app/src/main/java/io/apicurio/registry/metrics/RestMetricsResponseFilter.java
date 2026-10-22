package io.apicurio.registry.metrics;

import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.util.Priorities;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.Sample;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.ext.Provider;
import org.apache.commons.lang3.stream.Streams;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_OBSERVABILITY;
import static io.apicurio.registry.metrics.MetricsConstants.REST_REQUESTS;
import static io.apicurio.registry.metrics.MetricsConstants.REST_REQUESTS_DESCRIPTION;
import static io.apicurio.registry.metrics.MetricsConstants.REST_REQUESTS_TAG_METHOD;
import static io.apicurio.registry.metrics.MetricsConstants.REST_REQUESTS_TAG_PATH;
import static io.apicurio.registry.metrics.MetricsConstants.REST_REQUESTS_TAG_STATUS_CODE_GROUP;
import static io.apicurio.registry.metrics.MetricsConstants.VALUE_UNSPECIFIED;
import static java.lang.Boolean.TRUE;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;

/**
 * Filters REST API requests and responses to report metrics about them.
 */
@Provider
@PreMatching
@ApplicationScoped
@Priority(Priorities.RequestResponseFilters.APPLICATION)
public class RestMetricsResponseFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final String TIMER_SAMPLE_CONTEXT_PROPERTY_NAME = "request-timer-sample";
    private static final String SKIP_TIMER_SAMPLE_CONTEXT_PROPERTY_NAME = "skip-request-timer-sample";

    @Info(description = """
                    Enable collecting metrics about REST API requests.
            """, category = CATEGORY_OBSERVABILITY, availableSince = "3.1.1")
    @ConfigProperty(name = "apicurio.metrics.rest.enabled", defaultValue = "true")
    boolean enabled;

    @Info(description = """
                    Only requests with a path matching this pattern (Java syntax) will be considered for metrics collection. \
                    The pattern is applied to the request path only,
                    e.g. `curl 'http://localhost:8080/apis/registry/v3/groups/default/artifacts?offset=42&limit=10'`
                    will be matched against `/apis/registry/v3/groups/default/artifacts`.
            """, category = CATEGORY_OBSERVABILITY, availableSince = "3.1.1")
    @ConfigProperty(name = "apicurio.metrics.rest.path-filter-pattern", defaultValue = "/apis/.*")
    String pathFilterPatternRaw;

    @Info(description = """
                    Add the HTTP method tag/label on REST API request metrics. \
                    You might disable this tag to reduce metrics cardinality.
            """, category = CATEGORY_OBSERVABILITY, availableSince = "3.1.1")
    @ConfigProperty(name = "apicurio.metrics.rest.method-tag-enabled", defaultValue = "true")
    boolean methodTagEnabled;

    @Info(description = """
                    Add the unsubstituted path tag/label on REST API request metrics,
                    e.g. `/apis/registry/v3/groups/{groupId}/artifacts/{artifactId}`. \
                    You might disable this tag to reduce metrics cardinality. \
                    When an unsubstituted REST API resource path could not be determined,
                    the tag value will be `(unspecified)`.
            """, category = CATEGORY_OBSERVABILITY, availableSince = "3.1.1")
    @ConfigProperty(name = "apicurio.metrics.rest.path-tag-enabled", defaultValue = "true")
    boolean pathTagEnabled;

    @Info(description = """
                    Only the general category of the HTTP status code is added as a tag/label on REST API request metrics,
                    e.g. `1xx`, `2xx`, ... \
                    If you are interested in metrics for a specific status code, e.g. 401 for authentication errors,
                    this property allows you to specify a list of such codes. \
                    Each request will be counted only once, i.e. either as the specific status code or in the general category. \
            """, category = CATEGORY_OBSERVABILITY, availableSince = "3.1.1")
    @ConfigProperty(name = "apicurio.metrics.rest.explicit-status-codes-list", defaultValue = "401")
    List<String> explicitStatusCodesRaw;

    @Context
    ResourceInfo resourceInfo;

    @Inject
    MeterRegistry registry;

    @Inject
    Logger log;

    private Pattern pathFilterPattern;

    private Set<Integer> explicitStatusCodes;

    @PostConstruct
    void init() {
        if (enabled) {

            pathFilterPattern = Pattern.compile(pathFilterPatternRaw);
            explicitStatusCodes = explicitStatusCodesRaw.stream().map(Integer::valueOf).collect(Collectors.toSet());

            // See https://github.com/Apicurio/apicurio-registry/issues/6296
            Stream.concat(
                            Streams.of("1xx", "2xx", "3xx", "4xx", "5xx"),
                            explicitStatusCodes.stream().map(x -> Integer.toString(x))
                    )
                    .forEach(codeGroup -> {
                        // NOTE: We have to include the empty tag values,
                        // otherwise Micrometer will not create timers for the actual requests.
                        var b = Timer.builder(REST_REQUESTS)
                                .description(REST_REQUESTS_DESCRIPTION);
                        if (methodTagEnabled) {
                            b.tag(REST_REQUESTS_TAG_METHOD, VALUE_UNSPECIFIED);
                        }
                        if (pathTagEnabled) {
                            b.tag(REST_REQUESTS_TAG_PATH, VALUE_UNSPECIFIED);
                        }
                        b.tag(REST_REQUESTS_TAG_STATUS_CODE_GROUP, codeGroup)
                                .register(registry);
                    });
        } else {
            log.debug("REST API metrics collection is disabled.");
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        if (enabled && pathFilterPattern.matcher(requestContext.getUriInfo().getPath()).matches()) {
            requestContext.setProperty(TIMER_SAMPLE_CONTEXT_PROPERTY_NAME, Timer.start(registry));
        } else {
            requestContext.setProperty(SKIP_TIMER_SAMPLE_CONTEXT_PROPERTY_NAME, true);
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {

        if (requestContext.getProperty(TIMER_SAMPLE_CONTEXT_PROPERTY_NAME) != null) {

            var b = Timer.builder(REST_REQUESTS)
                    .description(REST_REQUESTS_DESCRIPTION);
            if (methodTagEnabled) {
                b.tag(REST_REQUESTS_TAG_METHOD, requestContext.getMethod());
            }
            if (pathTagEnabled) {
                b.tag(REST_REQUESTS_TAG_PATH, getPath());
            }
            var timer = b.tag(REST_REQUESTS_TAG_STATUS_CODE_GROUP, getStatusCodeGroup(responseContext.getStatus()))
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
        if (explicitStatusCodes.contains(statusCode)) {
            return Integer.toString(statusCode);
        }
        if (statusCode < 100 || statusCode >= 600) {
            return VALUE_UNSPECIFIED;
        }
        return format("%dxx", statusCode / 100);
    }

    private String getPath() {
        var path = ofNullable(resourceInfo.getResourceClass())
                .map(c -> c.getAnnotation(Path.class))
                .map(Path::value)
                .map(p -> p.endsWith("/") ? p.substring(0, p.length() - 1) : p);

        var methodPath = ofNullable(resourceInfo.getResourceMethod())
                .map(c -> c.getAnnotation(Path.class))
                .map(Path::value)
                .map(p -> p.startsWith("/") ? p : "/" + p);

        return path.map(p -> p + methodPath.orElse(""))
                .orElse(methodPath.orElse(VALUE_UNSPECIFIED));
    }
}
