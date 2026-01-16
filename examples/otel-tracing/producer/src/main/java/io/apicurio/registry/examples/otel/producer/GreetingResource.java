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
import org.apache.kafka.clients.producer.Producer;
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
    private static final String TOPIC = "greetings";

    @Inject
    GreetingProducer producer;

    @Inject
    Tracer tracer;

    /**
     * Send a single greeting message.
     *
     * Example: POST /greetings?name=World
     * Example with tenant: POST /greetings?name=World&tenantId=acme-corp
     *
     * @param name The name to greet (default: "World")
     * @param tenantId Optional tenant ID for baggage propagation demo
     * @return Response with trace information
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response sendGreeting(
            @QueryParam("name") @DefaultValue("World") String name,
            @QueryParam("tenantId") String tenantId) {

        String traceId = Span.current().getSpanContext().getTraceId();
        LOG.infof("Received request to send greeting for: %s (traceId: %s)", name, traceId);

        // Demonstrate baggage propagation - tenant ID flows across service boundaries
        if (tenantId != null && !tenantId.isEmpty()) {
            Baggage.current().toBuilder()
                    .put("tenant.id", tenantId)
                    .build().makeCurrent();
            Span.current().setAttribute("tenant.id", tenantId);
            LOG.infof("Set baggage tenant.id=%s", tenantId);
        }

        // Create a custom span for business logic
        Greeting greeting = createGreeting(name, traceId);

        String key = name.toLowerCase().replaceAll("\\s+", "-");

        Producer<String, Greeting> kafkaProducer = producer.createProducer("greeting-producer");
        try {
            producer.send(kafkaProducer, greeting, TOPIC, key);
            Span.current().addEvent("greeting-sent-to-kafka");
            LOG.infof("Greeting sent successfully for: %s", name);
        } finally {
            kafkaProducer.close();
        }

        return Response.accepted()
                .entity(Map.of(
                        "status", "accepted",
                        "name", name,
                        "traceId", traceId,
                        "tenantId", tenantId != null ? tenantId : "none",
                        "message", "Greeting message queued for delivery"
                ))
                .build();
    }

    /**
     * Create a greeting with a custom span demonstrating @WithSpan annotation.
     * This creates a child span automatically named after the method.
     */
    @WithSpan("create-greeting")
    protected Greeting createGreeting(
            @SpanAttribute("greeting.recipient") String name,
            @SpanAttribute("greeting.trace_id") String traceId) {

        Span currentSpan = Span.current();
        currentSpan.setAttribute("greeting.source", "greeting-producer");

        Greeting greeting = Greeting.newBuilder()
                .setMessage("Hello, " + name + "!")
                .setTimestamp(System.currentTimeMillis())
                .setSource("greeting-producer")
                .setTraceId(traceId)
                .build();

        currentSpan.addEvent("greeting-object-created");
        return greeting;
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

        Producer<String, Greeting> kafkaProducer = producer.createProducer("greeting-producer-batch");
        try {
            for (int i = 1; i <= count; i++) {
                String name = baseName + " #" + i;
                Greeting greeting = Greeting.newBuilder()
                        .setMessage("Hello, " + name + "!")
                        .setTimestamp(System.currentTimeMillis())
                        .setSource("greeting-producer")
                        .setTraceId(traceId)
                        .build();

                String key = name.toLowerCase().replaceAll("\\s+", "-");
                producer.send(kafkaProducer, greeting, TOPIC, key);
            }
            LOG.infof("Batch of %d greetings sent successfully", count);
        } finally {
            kafkaProducer.close();
        }

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
        String tenantId = Baggage.current().getEntryValue("tenant.id");
        return Response.ok(Map.of(
                "traceId", currentSpan.getSpanContext().getTraceId(),
                "spanId", currentSpan.getSpanContext().getSpanId(),
                "sampled", currentSpan.getSpanContext().isSampled(),
                "baggageTenantId", tenantId != null ? tenantId : "none"
        )).build();
    }

    /**
     * Error scenario endpoint for demonstrating error tracing.
     * Shows how to properly record errors and exceptions in spans.
     *
     * Example: POST /greetings/invalid
     * Example: POST /greetings/invalid?errorType=validation
     */
    @POST
    @Path("/invalid")
    @Produces(MediaType.APPLICATION_JSON)
    public Response sendInvalidGreeting(
            @QueryParam("errorType") @DefaultValue("validation") String errorType) {

        Span currentSpan = Span.current();
        String traceId = currentSpan.getSpanContext().getTraceId();

        LOG.warnf("Simulating error scenario: %s (traceId: %s)", errorType, traceId);

        // Create a child span for the failed operation
        Span errorSpan = tracer.spanBuilder("process-invalid-greeting")
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute("error.type", errorType)
                .startSpan();

        try (Scope scope = errorSpan.makeCurrent()) {
            // Simulate different error types
            Exception exception;
            String errorMessage;

            switch (errorType) {
                case "validation":
                    exception = new IllegalArgumentException("Greeting name cannot be empty");
                    errorMessage = "Validation failed: name is required";
                    break;
                case "schema":
                    exception = new RuntimeException("Schema validation failed: incompatible schema version");
                    errorMessage = "Schema registry error: incompatible schema";
                    break;
                case "kafka":
                    exception = new RuntimeException("Kafka producer timeout: broker not available");
                    errorMessage = "Kafka connection error";
                    break;
                default:
                    exception = new RuntimeException("Unknown error occurred");
                    errorMessage = "Internal server error";
            }

            // Record the exception in the span
            errorSpan.recordException(exception);
            errorSpan.setStatus(StatusCode.ERROR, errorMessage);
            errorSpan.addEvent("error-handled", io.opentelemetry.api.common.Attributes.builder()
                    .put("error.handled", true)
                    .put("error.recovery.action", "returned-error-response")
                    .build());

            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of(
                            "status", "error",
                            "errorType", errorType,
                            "message", errorMessage,
                            "traceId", traceId,
                            "hint", "View this trace in Jaeger to see error details"
                    ))
                    .build();
        } finally {
            errorSpan.end();
        }
    }

    /**
     * Endpoint demonstrating manual span creation with detailed attributes.
     * Shows how to create spans programmatically with the Tracer API.
     *
     * Example: POST /greetings/detailed?name=World&priority=high
     */
    @POST
    @Path("/detailed")
    @Produces(MediaType.APPLICATION_JSON)
    public Response sendDetailedGreeting(
            @QueryParam("name") @DefaultValue("World") String name,
            @QueryParam("priority") @DefaultValue("normal") String priority,
            @QueryParam("tenantId") String tenantId) {

        String traceId = Span.current().getSpanContext().getTraceId();

        // Create a custom span with detailed attributes
        Span processSpan = tracer.spanBuilder("process-detailed-greeting")
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute("greeting.recipient", name)
                .setAttribute("greeting.priority", priority)
                .setAttribute("greeting.topic", TOPIC)
                .startSpan();

        try (Scope scope = processSpan.makeCurrent()) {
            // Set baggage if tenant provided
            if (tenantId != null && !tenantId.isEmpty()) {
                Baggage.current().toBuilder()
                        .put("tenant.id", tenantId)
                        .build().makeCurrent();
                processSpan.setAttribute("tenant.id", tenantId);
            }

            processSpan.addEvent("validation-started");

            // Simulate validation
            if (name.length() > 100) {
                processSpan.setStatus(StatusCode.ERROR, "Name too long");
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "Name exceeds maximum length"))
                        .build();
            }

            processSpan.addEvent("validation-completed");

            // Create and send greeting
            Greeting greeting = Greeting.newBuilder()
                    .setMessage("Hello, " + name + "! [Priority: " + priority + "]")
                    .setTimestamp(System.currentTimeMillis())
                    .setSource("greeting-producer")
                    .setTraceId(traceId)
                    .build();

            processSpan.addEvent("greeting-created");

            Producer<String, Greeting> kafkaProducer = producer.createProducer("greeting-producer-detailed");
            try {
                producer.send(kafkaProducer, greeting, TOPIC, name.toLowerCase());
                processSpan.addEvent("greeting-sent", io.opentelemetry.api.common.Attributes.builder()
                        .put("kafka.topic", TOPIC)
                        .put("kafka.key", name.toLowerCase())
                        .build());
            } finally {
                kafkaProducer.close();
            }

            processSpan.setStatus(StatusCode.OK);

            return Response.accepted()
                    .entity(Map.of(
                            "status", "accepted",
                            "name", name,
                            "priority", priority,
                            "traceId", traceId,
                            "message", "Detailed greeting sent successfully"
                    ))
                    .build();
        } finally {
            processSpan.end();
        }
    }
}
