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

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Order entity representing an order in the database.
 * Changes to this table are captured by Debezium CDC.
 */
@Entity
@Table(name = "orders")
public class Order extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "customer_name", nullable = false)
    public String customerName;

    @Column(nullable = false)
    public String product;

    @Column(nullable = false)
    public Integer quantity = 1;

    @Column(nullable = false)
    public BigDecimal price;

    @Column(nullable = false)
    public String status = "PENDING";

    @Column(name = "trace_id")
    public String traceId;

    @Column(name = "tracingspancontext", columnDefinition = "TEXT")
    public String tracingspancontext;

    @Column(name = "created_at")
    public Instant createdAt;

    @Column(name = "updated_at")
    public Instant updatedAt;

    public Order() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Order(String customerName, String product, Integer quantity, BigDecimal price) {
        this();
        this.customerName = customerName;
        this.product = product;
        this.quantity = quantity;
        this.price = price;
    }
}
