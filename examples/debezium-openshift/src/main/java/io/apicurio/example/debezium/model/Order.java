package io.apicurio.example.debezium.model;

import example.inventory.orders.Value;
import lombok.*;

import java.time.Duration;
import java.time.Instant;

/**
 * @author Jakub Senko <em>m@jsenko.net</em>
 */
@Builder
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class Order {

    private Integer orderNumber;

    private Instant orderDate;

    private Integer purchaser;

    private Integer quantity;

    private Integer productId;

    public static Order from(Value value) {
        if (value == null) {
            return null;
        }
        return Order.builder()
                .orderNumber(value.getOrderNumber())
                .orderDate(Instant.EPOCH.plus(Duration.ofDays(value.getOrderDate())))
                .purchaser(value.getPurchaser())
                .quantity(value.getQuantity())
                .productId(value.getProductId())
                .build();
    }
}
