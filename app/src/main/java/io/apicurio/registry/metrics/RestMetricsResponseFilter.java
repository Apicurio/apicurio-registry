/*
 * Copyright 2020 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.apicurio.registry.metrics;

import static io.apicurio.registry.metrics.MetricIDs.REST_GROUP_TAG;
import static org.eclipse.microprofile.metrics.MetricRegistry.Type.APPLICATION;
import static org.eclipse.microprofile.metrics.MetricType.COUNTER;
import static org.eclipse.microprofile.metrics.MetricType.TIMER;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.ws.rs.Path;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.ext.Provider;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.Timer;
import org.eclipse.microprofile.metrics.annotation.RegistryType;

import io.smallrye.metrics.app.Clock;

/**
 * Filter class that filters REST API requests and responses to report metrics
 * about them. Binding of the methods being filtered is performed through the
 * {@link io.apicurio.registry.metrics.RestMetricsResponseFilteredNameBinding}
 * annotation added in the {@link io.apicurio.registry.rest.RegistryApplication}
 * JAX-RS Application class
 */
@Provider
@RestMetricsResponseFilteredNameBinding
public class RestMetricsResponseFilter implements ContainerRequestFilter, ContainerResponseFilter {

	@Inject
	@RegistryType(type = APPLICATION)
	MetricRegistry metricRegistry;

	String REST_HTTP_REQUESTS_TOTAL = "rest_http_requests_total";
	String REST_HTTP_REQUESTS_TOTAL_DESC = "Total number of REST HTTP Requests";

	String REST_HTTP_REQUESTS_TIME = "rest_http_request_duration";
	String REST_HTTP_REQUESTS_TIME_DESC = "Execution time of REST HTTP Requests in seconds";

	private Clock clock = Clock.defaultClock();

	public static final String REQUEST_START_TIME_CONTEXT_PROPERTY_NAME = "request-start-time";

	@Context
	private ResourceInfo resourceInfo;

	@Override
	public void filter(ContainerRequestContext requestContext) throws IOException {
		long sample = clock.getTick();
		requestContext.setProperty(REQUEST_START_TIME_CONTEXT_PROPERTY_NAME, sample);
	}

	@Override
	public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
			throws IOException {
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
				new Tag("status", String.format("%dxx", statusFamilyCode)), new Tag("endpoint", this.getUri()),
				new Tag("method", requestContext.getMethod()), };
		Counter statusFamilyCounter = metricRegistry.counter(metadata, counterTags);
		statusFamilyCounter.inc();

		long startTimeSample = (long) requestContext.getProperty(REQUEST_START_TIME_CONTEXT_PROPERTY_NAME);
		long endTimeSample = clock.getTick();
		long elapsed = endTimeSample - startTimeSample;

		final Metadata timerMetadata = Metadata.builder().withName(REST_HTTP_REQUESTS_TIME)
				.withDescription(REST_HTTP_REQUESTS_TIME_DESC).withType(TIMER).build();
		Tag[] timerTags = { new Tag("group", REST_GROUP_TAG), new Tag("metric", REST_HTTP_REQUESTS_TIME),
				new Tag("status", String.format("%dxx", statusFamilyCode)), new Tag("endpoint", this.getUri()),
				new Tag("method", requestContext.getMethod()), };
		Timer timer = metricRegistry.timer(timerMetadata, timerTags);
		timer.update(elapsed, TimeUnit.NANOSECONDS);
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
