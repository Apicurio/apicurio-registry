package io.apicurio.example.debezium.model;

import example.inventory.products.Value;
import lombok.*;

/**
 * @author Jakub Senko <em>m@jsenko.net</em>
 */
@Builder
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class Product {

    private Integer id;

    private String name;

    private String description;

    private Float weight;

    public static Product from(Value value) {
        if (value == null) {
            return null;
        }
        return Product.builder()
                .id(value.getId())
                .name(value.getName())
                .description(value.getDescription())
                .weight(value.getWeight())
                .build();
    }
}
