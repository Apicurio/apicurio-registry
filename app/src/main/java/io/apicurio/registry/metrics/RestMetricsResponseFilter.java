package io.apicurio.registry.metrics;

import static io.apicurio.registry.metrics.MetricIDs.*;

import static org.eclipse.microprofile.metrics.MetricRegistry.Type.APPLICATION;
import static org.eclipse.microprofile.metrics.MetricType.COUNTER;

import java.io.IOException;

import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.ext.Provider;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.annotation.RegistryType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Provider
@RestMetricsResponseFilteredNameBinding
public class RestMetricsResponseFilter implements ContainerResponseFilter {

	@Inject
	@RegistryType(type = APPLICATION)
	MetricRegistry metricRegistry;

	private static final Logger log = LoggerFactory.getLogger(RestMetricsResponseFilter.class);

	String REST_HTTP_REQUESTS_TOTAL = "rest_http_requests_total";
	String REST_HTTP_REQUESTS_TOTAL_DESC = "Total number of REST HTTP Requests";

	@Override
	public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
			throws IOException {
		log.info("'{} {}'", requestContext.getMethod(), requestContext.getUriInfo().getRequestUri().toString());
		log.info("Response code: '{}'", responseContext.getStatus());
		
		// Don't do anything when response code has not been set or when
		// response code is not between 1XX and 5XX
		int statusCode = responseContext.getStatus();
		if (statusCode == -1) {
			return;
		}
		if (statusCode < 100 || statusCode >= 600) {
			return;
		}

		final Metadata metadata = Metadata.builder().withName(REST_HTTP_REQUESTS_TOTAL)
				.withDescription(REST_HTTP_REQUESTS_TOTAL_DESC).withType(COUNTER).build();
		int statusFamilyCode = statusCode / 100;
		Tag[] counterTags = { new Tag("group", REST_GROUP_TAG), new Tag("metric", REST_HTTP_REQUESTS_TOTAL),
				new Tag("code", String.format("%dxx", statusFamilyCode)), };
		Counter statusFamilyCounter = metricRegistry.counter(metadata, counterTags);
		statusFamilyCounter.inc();
	}
}
