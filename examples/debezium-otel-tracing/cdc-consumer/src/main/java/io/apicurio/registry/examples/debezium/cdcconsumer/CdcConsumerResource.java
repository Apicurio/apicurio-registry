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

package io.apicurio.registry.examples.debezium.cdcconsumer;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * REST endpoint for consuming and viewing CDC events from Debezium.
 */
@Path("/cdc")
public class CdcConsumerResource {

    private static final Logger LOG = Logger.getLogger(CdcConsumerResource.class);

    @Inject
    CdcEventStore eventStore;

    /**
     * Get the next CDC event from the queue.
     */
    @GET
    @Path("/events")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getNextEvent() {
        String traceId = Span.current().getSpanContext().getTraceId();
        LOG.infof("Fetching next CDC event (traceId: %s)", traceId);

        Optional<CdcEventStore.CdcEvent> event = eventStore.poll();

        if (event.isEmpty()) {
            Span.current().addEvent("no-events-available");
            return Response.noContent()
                    .header("X-Trace-Id", traceId)
                    .build();
        }

        CdcEventStore.CdcEvent cdcEvent = event.get();
        Span.current().setAttribute("cdc.operation", cdcEvent.getOperation());
        Span.current().setAttribute("kafka.partition", cdcEvent.getPartition());
        Span.current().setAttribute("kafka.offset", cdcEvent.getOffset());
        Span.current().addEvent("cdc-event-returned");

        return Response.ok(Map.of(
                "event", cdcEvent.toMap(),
                "currentTraceId", traceId
        )).build();
    }

    /**
     * Get multiple CDC events.
     */
    @GET
    @Path("/events/batch")
    @Produces(MediaType.APPLICATION_JSON)
    @WithSpan("get-cdc-events-batch")
    public Response getBatchEvents(
            @QueryParam("count") @DefaultValue("10") @SpanAttribute("batch.requested_count") int count) {

        String traceId = Span.current().getSpanContext().getTraceId();
        LOG.infof("Fetching batch of up to %d CDC events (traceId: %s)", count, traceId);

        List<CdcEventStore.CdcEvent> events = eventStore.pollMany(count);

        Span.current().setAttribute("batch.actual_count", events.size());

        if (events.isEmpty()) {
            return Response.noContent()
                    .header("X-Trace-Id", traceId)
                    .build();
        }

        List<Map<String, Object>> eventMaps = events.stream()
                .map(CdcEventStore.CdcEvent::toMap)
                .collect(Collectors.toList());

        return Response.ok(Map.of(
                "count", events.size(),
                "events", eventMaps,
                "currentTraceId", traceId
        )).build();
    }

    /**
     * Get consumer statistics.
     */
    @GET
    @Path("/stats")
    @Produces(MediaType.APPLICATION_JSON)
    @WithSpan("get-cdc-stats")
    public Response getStats() {
        CdcEventStore.Stats stats = eventStore.getStats();

        Span.current().setAttribute("stats.queue_size", stats.queueSize);
        Span.current().setAttribute("stats.total_received", stats.totalReceived);

        return Response.ok(Map.of(
                "totalReceived", stats.totalReceived,
                "totalProcessed", stats.totalProcessed,
                "queueSize", stats.queueSize,
                "consumerRunning", stats.consumerRunning,
                "traceId", Span.current().getSpanContext().getTraceId()
        )).build();
    }

    /**
     * Health check endpoint.
     */
    @GET
    @Path("/health")
    @Produces(MediaType.APPLICATION_JSON)
    public Response health() {
        CdcEventStore.Stats stats = eventStore.getStats();
        String status = stats.consumerRunning ? "UP" : "DOWN";

        return Response.ok(Map.of(
                "status", status,
                "service", "cdc-consumer",
                "consumerRunning", stats.consumerRunning,
                "queueSize", stats.queueSize
        )).build();
    }

    /**
     * Get trace information.
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
