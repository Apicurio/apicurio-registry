package io.apicurio.example.debezium.model;

import example.inventory.products_on_hand.Value;
import lombok.*;

/**
 * @author Jakub Senko <em>m@jsenko.net</em>
 */
@Builder
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class ProductOnHand {

    private Integer productId;

    private Integer quantity;

    public static ProductOnHand from(Value value) {
        if (value == null) {
            return null;
        }
        return ProductOnHand.builder()
                .productId(value.getProductId())
                .quantity(value.getQuantity())
                .build();
    }
}
