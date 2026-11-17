package io.apicurio.registry.examples.serdes;

import java.util.Objects;

/**
 * A plain Java class with no Avro code: the reflect datum provider derives its Avro schema from the
 * fields (tour step 8).
 */
public class Customer {

    private String customerId;
    private String name;
    private int loyaltyPoints;

    public Customer() {
    }

    public Customer(String customerId, String name, int loyaltyPoints) {
        this.customerId = customerId;
        this.name = name;
        this.loyaltyPoints = loyaltyPoints;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Customer c && loyaltyPoints == c.loyaltyPoints
                && Objects.equals(customerId, c.customerId) && Objects.equals(name, c.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(customerId, name, loyaltyPoints);
    }

    @Override
    public String toString() {
        return "Customer[" + customerId + ", " + name + ", " + loyaltyPoints + "]";
    }
}
