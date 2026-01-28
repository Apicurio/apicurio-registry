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

package io.apicurio.registry.examples.kafka.order.producer;

import io.apicurio.registry.examples.kafka.order.Order;
import io.apicurio.registry.examples.kafka.order.OrderItem;
import io.apicurio.registry.examples.kafka.order.OrderStatus;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Utility class for generating realistic random orders for demonstration purposes.
 *
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
public class OrderGenerator {

    private static final String[] FIRST_NAMES = {
        "John", "Jane", "Michael", "Sarah", "David", "Emily", "Robert", "Lisa",
        "William", "Maria", "James", "Jennifer", "Thomas", "Patricia", "Charles", "Linda"
    };

    private static final String[] LAST_NAMES = {
        "Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis",
        "Rodriguez", "Martinez", "Hernandez", "Lopez", "Gonzalez", "Wilson", "Anderson", "Taylor"
    };

    private static final String[][] PRODUCTS = {
        {"LAPTOP-001", "Professional Laptop", "899.99"},
        {"MOUSE-002", "Wireless Mouse", "29.99"},
        {"KEYBOARD-003", "Mechanical Keyboard", "79.99"},
        {"MONITOR-004", "27-inch Monitor", "299.99"},
        {"HEADSET-005", "Noise-Canceling Headset", "149.99"},
        {"WEBCAM-006", "4K Webcam", "89.99"},
        {"CHAIR-007", "Ergonomic Office Chair", "249.99"},
        {"DESK-008", "Standing Desk", "399.99"},
        {"TABLET-009", "10-inch Tablet", "329.99"},
        {"PHONE-010", "Smartphone", "699.99"}
    };

    private final Random random = new Random();

    /**
     * Generates a random order with realistic customer and product data.
     *
     * @return a randomly generated Order
     */
    public Order generateOrder() {
        String orderId = UUID.randomUUID().toString();
        String customerId = "CUST-" + String.format("%06d", random.nextInt(100000));
        String firstName = FIRST_NAMES[random.nextInt(FIRST_NAMES.length)];
        String lastName = LAST_NAMES[random.nextInt(LAST_NAMES.length)];
        String customerName = firstName + " " + lastName;
        String customerEmail = firstName.toLowerCase() + "." + lastName.toLowerCase() + "@example.com";

        long orderTimestamp = System.currentTimeMillis();

        List<OrderItem> items = generateOrderItems();
        double totalAmount = calculateTotalAmount(items);

        Order order = Order.newBuilder()
            .setOrderId(orderId)
            .setCustomerId(customerId)
            .setCustomerName(customerName)
            .setCustomerEmail(customerEmail)
            .setOrderTimestamp(orderTimestamp)
            .setItems(items)
            .setTotalAmount(totalAmount)
            .setStatus(OrderStatus.PENDING)
            .build();

        return order;
    }

    /**
     * Generates a random list of order items.
     *
     * @return a list of OrderItem objects
     */
    private List<OrderItem> generateOrderItems() {
        List<OrderItem> items = new ArrayList<>();
        int itemCount = random.nextInt(3) + 1; // 1 to 3 items per order

        for (int i = 0; i < itemCount; i++) {
            String[] product = PRODUCTS[random.nextInt(PRODUCTS.length)];
            int quantity = random.nextInt(3) + 1; // 1 to 3 quantity

            OrderItem item = OrderItem.newBuilder()
                .setItemId(product[0])
                .setItemName(product[1])
                .setQuantity(quantity)
                .setUnitPrice(Double.parseDouble(product[2]))
                .build();

            items.add(item);
        }

        return items;
    }

    /**
     * Calculates the total amount for a list of order items.
     *
     * @param items the list of order items
     * @return the total amount
     */
    private double calculateTotalAmount(List<OrderItem> items) {
        return items.stream()
            .mapToDouble(item -> item.getQuantity() * item.getUnitPrice())
            .sum();
    }
}
