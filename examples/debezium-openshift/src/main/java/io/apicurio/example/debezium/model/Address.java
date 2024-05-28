package io.apicurio.example.debezium.model;

import example.inventory.addresses.Value;
import lombok.*;

/**
 * @author Jakub Senko <em>m@jsenko.net</em>
 */
@Builder
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class Address {

    private Integer id;

    private Integer customerId;

    private String street;

    private String city;

    private String state;

    private String zip;

    private String type;

    public static Address from(Value value) {
        if (value == null) {
            return null;
        }
        return Address.builder()
                .id(value.getId())
                .customerId(value.getCustomerId())
                .street(value.getStreet())
                .city(value.getCity())
                .state(value.getState())
                .zip(value.getZip())
                .type(value.getType())
                .build();
    }
}
