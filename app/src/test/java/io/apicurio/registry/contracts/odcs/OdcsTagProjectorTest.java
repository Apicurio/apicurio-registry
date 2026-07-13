package io.apicurio.registry.contracts.odcs;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ensures field-tag projection only uses the {@code schemas[]} entry for the target artifact (#8104 / PR review).
 */
class OdcsTagProjectorTest {

    @Test
    void parseSchemaLocationAllowsOptionalGroupAndVersion() {
        assertEquals("orders", OdcsSchemaLocations.parse("orders/OrderEvent:3", "default")[0]);
        assertEquals("OrderEvent", OdcsSchemaLocations.parse("orders/OrderEvent:3", "default")[1]);

        assertEquals("default", OdcsSchemaLocations.parse("OrderEvent:latest", "default")[0]);
        assertEquals("OrderEvent", OdcsSchemaLocations.parse("OrderEvent:latest", "default")[1]);

        assertEquals("default", OdcsSchemaLocations.parse("OrderEvent", "default")[0]);
        assertEquals("OrderEvent", OdcsSchemaLocations.parse("OrderEvent", "default")[1]);

        assertFalse(OdcsSchemaLocations.isValid(OdcsSchemaLocations.parse(null, "default")));
        assertFalse(OdcsSchemaLocations.isValid(OdcsSchemaLocations.parse("  ", "default")));
    }

    @Test
    void matchesTargetOnlyForOwnLocation() {
        OdcsSchema order = OdcsSchema.builder()
                .name("OrderEvent")
                .location("orders/OrderEvent:3")
                .build();
        OdcsSchema address = OdcsSchema.builder()
                .name("Address")
                .location("shared/Address:1")
                .build();

        assertTrue(OdcsTagProjector.matchesTarget(order, "orders", "OrderEvent"));
        assertFalse(OdcsTagProjector.matchesTarget(order, "shared", "Address"));
        assertTrue(OdcsTagProjector.matchesTarget(address, "shared", "Address"));
        assertFalse(OdcsTagProjector.matchesTarget(address, "orders", "OrderEvent"));
    }

    @Test
    void findMatchingSchemaIgnoresOtherEntries() {
        OdcsSchema order = OdcsSchema.builder()
                .name("OrderEvent")
                .location("orders/OrderEvent")
                .build();
        OdcsSchema address = OdcsSchema.builder()
                .name("Address")
                .location("shared/Address")
                .build();

        OdcsSchema matched = OdcsTagProjector.findMatchingSchema(
                List.of(order, address), "shared", "Address");
        assertNotNull(matched);
        assertEquals("Address", matched.getName());

        assertNull(OdcsTagProjector.findMatchingSchema(
                List.of(order, address), "other", "Thing"));
    }
}
