/*
 * Copyright 2024 Red Hat Inc
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

package io.apicurio.registry.examples.otel.producer;

import io.opentelemetry.api.trace.Span;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.Map;

/**
 * REST endpoint for triggering greeting message production.
 *
 * This resource demonstrates how HTTP requests create traces that are
 * propagated through to Kafka messages and ultimately to the consumer.
 */
@Path("/greetings")
public class GreetingResource {

    private static final Logger LOG = Logger.getLogger(GreetingResource.class);

    @Inject
    GreetingProducer producer;

    /**
     * Send a single greeting message.
     *
     * Example: POST /greetings?name=World
     *
     * @param name The name to greet (default: "World")
     * @return Response with trace information
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response sendGreeting(@QueryParam("name") @DefaultValue("World") String name) {
        String traceId = Span.current().getSpanContext().getTraceId();
        LOG.infof("Received request to send greeting for: %s (traceId: %s)", name, traceId);

        producer.sendGreeting(name);

        return Response.accepted()
                .entity(Map.of(
                        "status", "accepted",
                        "name", name,
                        "traceId", traceId,
                        "message", "Greeting message queued for delivery"
                ))
                .build();
    }

    /**
     * Send multiple greeting messages for testing distributed tracing under load.
     *
     * Example: POST /greetings/batch?baseName=Test&count=10
     *
     * @param baseName The base name to use (default: "User")
     * @param count The number of messages to send (default: 5)
     * @return Response with batch information
     */
    @POST
    @Path("/batch")
    @Produces(MediaType.APPLICATION_JSON)
    public Response sendBatchGreetings(
            @QueryParam("baseName") @DefaultValue("User") String baseName,
            @QueryParam("count") @DefaultValue("5") int count) {

        String traceId = Span.current().getSpanContext().getTraceId();
        LOG.infof("Received request to send %d greetings with baseName: %s (traceId: %s)",
                count, baseName, traceId);

        producer.sendMultipleGreetings(baseName, count);

        return Response.accepted()
                .entity(Map.of(
                        "status", "accepted",
                        "baseName", baseName,
                        "count", count,
                        "traceId", traceId,
                        "message", String.format("%d greeting messages queued for delivery", count)
                ))
                .build();
    }

    /**
     * Health check endpoint.
     */
    @GET
    @Path("/health")
    @Produces(MediaType.APPLICATION_JSON)
    public Response health() {
        return Response.ok(Map.of(
                "status", "UP",
                "service", "greeting-producer"
        )).build();
    }

    /**
     * Get tracing information for debugging.
     */
    @GET
    @Path("/trace-info")
    @Produces(MediaType.APPLICATION_JSON)
    public Response traceInfo() {
        Span currentSpan = Span.current();
        return Response.ok(Map.of(
                "traceId", currentSpan.getSpanContext().getTraceId(),
                "spanId", currentSpan.getSpanContext().getSpanId(),
                "sampled", currentSpan.getSpanContext().isSampled()
        )).build();
    }
}
