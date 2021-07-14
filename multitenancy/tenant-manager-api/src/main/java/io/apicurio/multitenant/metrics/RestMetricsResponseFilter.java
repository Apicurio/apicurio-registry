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
package io.apicurio.multitenant.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Default;
import javax.inject.Inject;
import javax.ws.rs.Path;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.ext.Provider;

import static io.apicurio.multitenant.metrics.MetricsConstants.*;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Filters REST API requests and responses to report metrics
 * about them.
 *
 * @author Miguel Soriano
 * @author Jakub Senko <jsenko@redhat.com>
 */
@Provider
@Default
@ApplicationScoped
public class RestMetricsResponseFilter implements ContainerRequestFilter, ContainerResponseFilter {

    @Inject
    Logger log;

    @Inject
    MeterRegistry registry;

    public static final String TIMER_SAMPLE_CONTEXT_PROPERTY_NAME = "request-timer-sample";

    @Context
    private ResourceInfo resourceInfo;

    static final Pattern ENABLED_PATTERN = Pattern.compile("/api/.*");

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
            .description(REST_REQUESTS_TIMER_DESCRIPTION)
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
