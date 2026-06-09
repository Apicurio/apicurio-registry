/*
 * Copyright 2025 Red Hat
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

package io.apicurio.registry.rest;

import io.apicurio.registry.observability.OTelAttributes;
import io.apicurio.registry.util.Priorities;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import jakarta.annotation.Priority;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

/**
 * JAX-RS response filter that records HTTP response status codes on OpenTelemetry spans
 * and marks server errors (5xx) with ERROR status for trace UI visibility.
 */
@Provider
@Priority(Priorities.RequestResponseFilters.APPLICATION)
public class TracingResponseFilter implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        Span currentSpan = Span.current();
        if (currentSpan.isRecording()) {
            int statusCode = responseContext.getStatus();
            currentSpan.setAttribute(OTelAttributes.ATTR_HTTP_RESPONSE_STATUS_CODE, (long) statusCode);

            if (statusCode >= 500) {
                currentSpan.setStatus(StatusCode.ERROR, "HTTP " + statusCode);
            }
        }
    }
}
