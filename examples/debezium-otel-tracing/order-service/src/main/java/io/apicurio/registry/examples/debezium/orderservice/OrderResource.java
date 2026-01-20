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

package io.apicurio.registry.examples.debezium.orderservice;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * REST endpoint for managing orders.
 *
 * Orders are persisted to PostgreSQL, where Debezium captures changes
 * and publishes them to Kafka with OpenTelemetry trace context.
 */
@Path("/orders")
public class OrderResource {

    private static final Logger LOG = Logger.getLogger(OrderResource.class);

    @Inject
    Tracer tracer;

    /**
     * Create a new order.
     * The trace ID is stored in the order for correlation with CDC events.
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public Response createOrder(OrderRequest request) {
        String traceId = Span.current().getSpanContext().getTraceId();
        LOG.infof("Creating order for %s (traceId: %s)", request.customerName, traceId);

        // Create order with trace context
        Order order = createOrderEntity(request, traceId);

        Span.current().setAttribute("order.id", order.id);
        Span.current().setAttribute("order.product", order.product);
        Span.current().setAttribute("order.quantity", order.quantity);
        Span.current().addEvent("order-created");

        LOG.infof("Order created with ID: %d (traceId: %s)", order.id, traceId);

        return Response.status(Response.Status.CREATED)
                .entity(Map.of(
                        "id", order.id,
                        "status", "created",
                        "traceId", traceId,
                        "message", "Order created and will be captured by Debezium CDC"
                ))
                .build();
    }

    @WithSpan("create-order-entity")
    @Transactional
    protected Order createOrderEntity(
            OrderRequest request,
            @SpanAttribute("order.trace_id") String traceId) {

        Order order = new Order();
        order.customerName = request.customerName;
        order.product = request.product;
        order.quantity = request.quantity != null ? request.quantity : 1;
        order.price = request.price != null ? request.price : BigDecimal.ZERO;
        order.traceId = traceId;
        order.persist();

        return order;
    }

    /**
     * Update order status.
     * This generates an UPDATE CDC event captured by Debezium.
     */
    @PUT
    @Path("/{id}/status")
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public Response updateOrderStatus(
            @PathParam("id") Long id,
            @QueryParam("status") @DefaultValue("CONFIRMED") String status) {

        String traceId = Span.current().getSpanContext().getTraceId();
        LOG.infof("Updating order %d to status %s (traceId: %s)", id, status, traceId);

        Order order = Order.findById(id);
        if (order == null) {
            Span.current().setStatus(StatusCode.ERROR, "Order not found");
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Order not found", "id", id))
                    .build();
        }

        String previousStatus = order.status;
        order.status = status;
        order.updatedAt = Instant.now();

        Span.current().setAttribute("order.id", id);
        Span.current().setAttribute("order.previous_status", previousStatus);
        Span.current().setAttribute("order.new_status", status);
        Span.current().addEvent("order-status-updated");

        return Response.ok(Map.of(
                "id", order.id,
                "previousStatus", previousStatus,
                "newStatus", status,
                "traceId", traceId,
                "message", "Order status updated - CDC event will be generated"
        )).build();
    }

    /**
     * Get all orders.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @WithSpan("list-orders")
    public Response listOrders() {
        List<Order> orders = Order.listAll();
        Span.current().setAttribute("orders.count", orders.size());
        return Response.ok(orders).build();
    }

    /**
     * Get order by ID.
     */
    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getOrder(@PathParam("id") Long id) {
        Order order = Order.findById(id);
        if (order == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Order not found", "id", id))
                    .build();
        }

        Span.current().setAttribute("order.id", order.id);
        return Response.ok(order).build();
    }

    /**
     * Create multiple orders for batch testing.
     */
    @POST
    @Path("/batch")
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public Response createBatchOrders(
            @QueryParam("count") @DefaultValue("5") int count) {

        String traceId = Span.current().getSpanContext().getTraceId();
        LOG.infof("Creating batch of %d orders (traceId: %s)", count, traceId);

        Span batchSpan = tracer.spanBuilder("create-order-batch")
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute("batch.size", count)
                .startSpan();

        try (Scope scope = batchSpan.makeCurrent()) {
            for (int i = 1; i <= count; i++) {
                Order order = new Order();
                order.customerName = "Batch Customer #" + i;
                order.product = "Product-" + i;
                order.quantity = i;
                order.price = new BigDecimal("19.99").multiply(new BigDecimal(i));
                order.traceId = traceId;
                order.persist();

                batchSpan.addEvent("order-created", io.opentelemetry.api.common.Attributes.builder()
                        .put("order.id", order.id)
                        .put("order.sequence", i)
                        .build());
            }

            batchSpan.setStatus(StatusCode.OK);

            return Response.status(Response.Status.CREATED)
                    .entity(Map.of(
                            "count", count,
                            "traceId", traceId,
                            "message", String.format("%d orders created - CDC events will be generated", count)
                    ))
                    .build();
        } finally {
            batchSpan.end();
        }
    }

    /**
     * Health check endpoint.
     */
    @GET
    @Path("/health")
    @Produces(MediaType.APPLICATION_JSON)
    public Response health() {
        long orderCount = Order.count();
        return Response.ok(Map.of(
                "status", "UP",
                "service", "order-service",
                "orderCount", orderCount
        )).build();
    }

    /**
     * Get current trace information.
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

    /**
     * Request DTO for creating orders.
     */
    public static class OrderRequest {
        public String customerName;
        public String product;
        public Integer quantity;
        public BigDecimal price;
    }
}
