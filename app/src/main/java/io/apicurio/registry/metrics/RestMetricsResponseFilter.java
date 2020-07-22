package io.apicurio.registry.metrics;

import static io.apicurio.registry.metrics.MetricIDs.REST_GROUP_TAG;
import static org.eclipse.microprofile.metrics.MetricRegistry.Type.APPLICATION;
import static org.eclipse.microprofile.metrics.MetricType.COUNTER;

import java.io.IOException;

import javax.inject.Inject;
import javax.ws.rs.Path;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
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

	@Context
	private ResourceInfo resourceInfo;

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
		int statusFamilyCode = statusCode / 100;

		final Metadata metadata = Metadata.builder().withName(REST_HTTP_REQUESTS_TOTAL)
				.withDescription(REST_HTTP_REQUESTS_TOTAL_DESC).withType(COUNTER).build();
		Tag[] counterTags = { new Tag("group", REST_GROUP_TAG), new Tag("metric", REST_HTTP_REQUESTS_TOTAL),
				new Tag("status", String.format("%dxx", statusFamilyCode)),
				// TODO this information those not include base path (/api). Should we include
				// it as part
				// of the endpoint tag? or being in the rest_http_requests_total context it's
				// assumed that user
				// knows the path? maybe another tag named basePath?
				new Tag("endpoint", this.getUri()), new Tag("method", requestContext.getMethod()), };
		Counter statusFamilyCounter = metricRegistry.counter(metadata, counterTags);
		statusFamilyCounter.inc();
	}

	private String getUri() {
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
