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

package io.apicurio.registry.examples.otel.consumer;

import io.apicurio.registry.examples.otel.Greeting;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
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

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST endpoint for consuming greeting messages and observability.
 *
 * This resource demonstrates:
 * - Consuming from a background consumer (realistic pattern)
 * - Custom span creation with detailed attributes
 * - Baggage extraction from producer
 * - Trace statistics and observability endpoints
 */
@Path("/consumer")
public class ConsumerResource {

    private static final Logger LOG = Logger.getLogger(ConsumerResource.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault());

    @Inject
    GreetingMessageStore messageStore;

    @Inject
    Tracer tracer;

    /**
     * Consume a single greeting message from the store.
     * The background consumer continuously receives messages and stores them.
     * This endpoint retrieves from that store with full trace context.
     */
    @GET
    @Path("/greetings")
    @Produces(MediaType.APPLICATION_JSON)
    public Response consumeGreeting() {
        String traceId = Span.current().getSpanContext().getTraceId();
        LOG.infof("Consuming greeting message (traceId: %s)", traceId);

        Optional<GreetingMessageStore.ReceivedGreeting> received = messageStore.poll();

        if (received.isEmpty()) {
            Span.current().addEvent("no-messages-available");
            return Response.noContent()
                    .header("X-Trace-Id", traceId)
                    .build();
        }

        return buildGreetingResponse(received.get(), traceId);
    }

    /**
     * Consume multiple greeting messages at once.
     * Demonstrates batch processing with tracing.
     *
     * Example: GET /consumer/greetings/batch?count=5
     */
    @GET
    @Path("/greetings/batch")
    @Produces(MediaType.APPLICATION_JSON)
    @WithSpan("consume-greeting-batch")
    public Response consumeBatch(
            @QueryParam("count") @DefaultValue("5") @SpanAttribute("batch.requested_count") int count) {

        String traceId = Span.current().getSpanContext().getTraceId();
        LOG.infof("Consuming batch of up to %d greetings (traceId: %s)", count, traceId);

        List<GreetingMessageStore.ReceivedGreeting> messages = messageStore.pollMany(count);

        Span.current().setAttribute("batch.actual_count", messages.size());

        if (messages.isEmpty()) {
            Span.current().addEvent("no-messages-available");
            return Response.noContent()
                    .header("X-Trace-Id", traceId)
                    .build();
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (GreetingMessageStore.ReceivedGreeting received : messages) {
            results.add(buildGreetingMap(received));
        }

        Span.current().addEvent("batch-consumed", io.opentelemetry.api.common.Attributes.builder()
                .put("batch.size", messages.size())
                .build());

        return Response.ok(Map.of(
                "count", messages.size(),
                "currentTraceId", traceId,
                "messages", results
        )).build();
    }

    /**
     * Process a greeting with simulated business logic.
     * Demonstrates creating child spans for processing steps.
     *
     * Example: POST /consumer/greetings/process
     */
    @POST
    @Path("/greetings/process")
    @Produces(MediaType.APPLICATION_JSON)
    public Response processGreeting() {
        String traceId = Span.current().getSpanContext().getTraceId();

        Optional<GreetingMessageStore.ReceivedGreeting> received = messageStore.poll();

        if (received.isEmpty()) {
            return Response.noContent()
                    .header("X-Trace-Id", traceId)
                    .build();
        }

        GreetingMessageStore.ReceivedGreeting msg = received.get();
        Greeting greeting = msg.getGreeting();

        // Create a processing span with detailed steps
        Span processSpan = tracer.spanBuilder("business-logic-processing")
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute("kafka.partition", msg.getPartition())
                .setAttribute("kafka.offset", msg.getOffset())
                .setAttribute("greeting.source", greeting.getSource().toString())
                .startSpan();

        try (Scope scope = processSpan.makeCurrent()) {
            // Step 1: Validate
            processSpan.addEvent("validation-started");
            validateGreeting(greeting);
            processSpan.addEvent("validation-completed");

            // Step 2: Transform
            processSpan.addEvent("transformation-started");
            String transformed = transformGreeting(greeting);
            processSpan.addEvent("transformation-completed");

            // Step 3: Extract tenant from baggage (if present)
            String tenantId = msg.getTenantId();
            if (tenantId != null) {
                processSpan.setAttribute("tenant.id", tenantId);
                processSpan.addEvent("tenant-context-applied");
            }

            processSpan.setStatus(StatusCode.OK);

            return Response.ok(Map.of(
                    "status", "processed",
                    "originalMessage", greeting.getMessage().toString(),
                    "transformedMessage", transformed,
                    "kafkaPartition", msg.getPartition(),
                    "kafkaOffset", msg.getOffset(),
                    "originalTraceId", greeting.getTraceId() != null ? greeting.getTraceId().toString() : "unknown",
                    "extractedTraceId", msg.getExtractedTraceId(),
                    "currentTraceId", traceId,
                    "tenantId", tenantId != null ? tenantId : "none"
            )).build();

        } catch (Exception e) {
            processSpan.recordException(e);
            processSpan.setStatus(StatusCode.ERROR, e.getMessage());
            return Response.serverError()
                    .entity(Map.of("error", e.getMessage(), "traceId", traceId))
                    .build();
        } finally {
            processSpan.end();
        }
    }

    @WithSpan("validate-greeting")
    protected void validateGreeting(Greeting greeting) {
        Span.current().setAttribute("greeting.message_length", greeting.getMessage().length());
        // Simulated validation
        if (greeting.getMessage() == null || greeting.getMessage().toString().isEmpty()) {
            throw new IllegalArgumentException("Greeting message cannot be empty");
        }
    }

    @WithSpan("transform-greeting")
    protected String transformGreeting(Greeting greeting) {
        String original = greeting.getMessage().toString();
        String transformed = original.toUpperCase() + " [Processed at " +
                FORMATTER.format(Instant.now()) + "]";
        Span.current().setAttribute("transformed.length", transformed.length());
        return transformed;
    }

    private Response buildGreetingResponse(GreetingMessageStore.ReceivedGreeting received, String traceId) {
        Greeting greeting = received.getGreeting();
        String formattedTime = FORMATTER.format(Instant.ofEpochMilli(greeting.getTimestamp()));

        // Add Kafka metadata to current span
        Span currentSpan = Span.current();
        currentSpan.setAttribute("kafka.partition", received.getPartition());
        currentSpan.setAttribute("kafka.offset", received.getOffset());
        currentSpan.setAttribute("kafka.key", received.getKey() != null ? received.getKey() : "null");

        LOG.infof("Returning greeting: '%s' from partition %d, offset %d (currentTraceId: %s, originalTraceId: %s)",
                greeting.getMessage(),
                received.getPartition(),
                received.getOffset(),
                traceId,
                greeting.getTraceId());

        currentSpan.addEvent("greeting-returned");

        Map<String, Object> response = new HashMap<>();
        response.put("message", greeting.getMessage().toString());
        response.put("source", greeting.getSource().toString());
        response.put("timestamp", greeting.getTimestamp());
        response.put("formattedTime", formattedTime);
        response.put("kafkaPartition", received.getPartition());
        response.put("kafkaOffset", received.getOffset());
        response.put("kafkaKey", received.getKey() != null ? received.getKey() : "null");
        response.put("originalTraceId", greeting.getTraceId() != null ? greeting.getTraceId().toString() : "unknown");
        response.put("extractedTraceId", received.getExtractedTraceId());
        response.put("currentTraceId", traceId);
        response.put("tenantId", received.getTenantId() != null ? received.getTenantId() : "none");

        return Response.ok(response).build();
    }

    private Map<String, Object> buildGreetingMap(GreetingMessageStore.ReceivedGreeting received) {
        Greeting greeting = received.getGreeting();
        Map<String, Object> map = new HashMap<>();
        map.put("message", greeting.getMessage().toString());
        map.put("source", greeting.getSource().toString());
        map.put("timestamp", greeting.getTimestamp());
        map.put("formattedTime", FORMATTER.format(Instant.ofEpochMilli(greeting.getTimestamp())));
        map.put("kafkaPartition", received.getPartition());
        map.put("kafkaOffset", received.getOffset());
        map.put("originalTraceId", greeting.getTraceId() != null ? greeting.getTraceId().toString() : "unknown");
        map.put("extractedTraceId", received.getExtractedTraceId());
        map.put("tenantId", received.getTenantId() != null ? received.getTenantId() : "none");
        return map;
    }

    /**
     * Get consumer statistics and trace metrics.
     * Useful for observability dashboards.
     */
    @GET
    @Path("/stats")
    @Produces(MediaType.APPLICATION_JSON)
    @WithSpan("get-consumer-stats")
    public Response getStats() {
        GreetingMessageStore.Stats stats = messageStore.getStats();

        Span.current().setAttribute("stats.queue_size", stats.queueSize);
        Span.current().setAttribute("stats.total_received", stats.totalReceived);

        return Response.ok(Map.of(
                "totalReceived", stats.totalReceived,
                "totalProcessed", stats.totalProcessed,
                "totalErrors", stats.totalErrors,
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
        GreetingMessageStore.Stats stats = messageStore.getStats();
        String status = stats.consumerRunning ? "UP" : "DOWN";

        return Response.ok(Map.of(
                "status", status,
                "service", "greeting-consumer",
                "consumerRunning", stats.consumerRunning,
                "queueSize", stats.queueSize
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
        String tenantId = Baggage.current().getEntryValue("tenant.id");

        return Response.ok(Map.of(
                "traceId", currentSpan.getSpanContext().getTraceId(),
                "spanId", currentSpan.getSpanContext().getSpanId(),
                "sampled", currentSpan.getSpanContext().isSampled(),
                "baggageTenantId", tenantId != null ? tenantId : "none"
        )).build();
    }
}
