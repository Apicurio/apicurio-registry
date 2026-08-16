package io.apicurio.registry.contracts;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

public class ContractLabelsTest {

    @Test
    public void testDetectContractId() {
        Map<String, String> labels = new HashMap<>();
        labels.put("contract.orders.id", "orders");
        labels.put("contract.orders.status", "STABLE");

        Assertions.assertEquals("orders", ContractLabels.detectContractId(labels));
    }

    @Test
    public void testDetectContractId_NotFound() {
        Map<String, String> labels = new HashMap<>();
        labels.put("contract.orders.status", "STABLE");

        Assertions.assertNull(ContractLabels.detectContractId(labels));
    }

    @Test
    public void testDetectContractId_InvalidNestedContractId() {
        Map<String, String> labels = new HashMap<>();
        labels.put("contract.orders.api.id", "orders-api");

        Assertions.assertNull(ContractLabels.detectContractId(labels));
    }

    @Test
    public void testDetectContractId_EmptyLabels() {
        Assertions.assertNull(ContractLabels.detectContractId(new HashMap<>()));
    }
}