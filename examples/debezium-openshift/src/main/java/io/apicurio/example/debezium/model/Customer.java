package io.apicurio.example.debezium.model;

import example.inventory.customers.Value;
import lombok.*;

/**
 * @author Jakub Senko <em>m@jsenko.net</em>
 */
@Builder
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class Customer {

    private Integer id;

    private String firstName;

    private String lastName;

    private String email;

    public static Customer from(Value value) {
        if (value == null) {
            return null;
        }
        return Customer.builder()
                .id(value.getId())
                .firstName(value.getFirstName())
                .lastName(value.getLastName())
                .email(value.getEmail())
                .build();
    }
}
