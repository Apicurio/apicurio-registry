package io.apicurio.registry.contracts.odcs;

import io.apicurio.registry.storage.RegistryStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Ensures field-tag projection only uses the {@code schemas[]} entry for the target artifact (#8104 / PR review).
 */
class OdcsTagProjectorTest {

    private RegistryStorage storage;
    private OdcsTagProjector projector;

    @BeforeEach
    void setUp() throws Exception {
        storage = mock(RegistryStorage.class);
        projector = new OdcsTagProjector();
        Field storageField = OdcsTagProjector.class.getDeclaredField("storage");
        storageField.setAccessible(true);
        storageField.set(projector, storage);
    }

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

    @Test
    void findMatchingSchemaUsesFirstMatchWhenDuplicateLocations() {
        Map<String, OdcsFieldMetadata> firstFields = Map.of(
                "orderId", OdcsFieldMetadata.builder().pii(true).build());
        Map<String, OdcsFieldMetadata> secondFields = Map.of(
                "customerId", OdcsFieldMetadata.builder().pii(true).build());

        OdcsSchema first = OdcsSchema.builder()
                .name("OrderEvent")
                .location("orders/OrderEvent")
                .fields(firstFields)
                .build();
        OdcsSchema duplicate = OdcsSchema.builder()
                .name("OrderEventDuplicate")
                .location("orders/OrderEvent")
                .fields(secondFields)
                .build();

        OdcsSchema matched = OdcsTagProjector.findMatchingSchema(
                List.of(first, duplicate), "orders", "OrderEvent");
        assertNotNull(matched);
        assertEquals("OrderEvent", matched.getName());
        assertTrue(matched.getFields().containsKey("orderId"));
        assertFalse(matched.getFields().containsKey("customerId"));
    }

    @Test
    void projectAppliesOnlyMatchingSchemaFieldTags() {
        when(storage.getArtifactVersions("orders", "OrderEvent")).thenReturn(List.of("1"));
        when(storage.getArtifactVersions("shared", "Address")).thenReturn(List.of("2"));

        OdcsSchema order = OdcsSchema.builder()
                .name("OrderEvent")
                .location("orders/OrderEvent")
                .fields(Map.of("orderId", OdcsFieldMetadata.builder().pii(true).build()))
                .build();
        OdcsSchema address = OdcsSchema.builder()
                .name("Address")
                .location("shared/Address")
                .fields(Map.of("street", OdcsFieldMetadata.builder().tags(List.of("PII")).build()))
                .build();
        OdcsContract contract = OdcsContract.builder()
                .schemas(List.of(order, address))
                .build();

        List<String> warnings = new ArrayList<>();
        int count = projector.project(contract, "contract-1", "orders", "OrderEvent", warnings);

        assertEquals(1, count);
        assertTrue(warnings.isEmpty());

        ArgumentCaptor<Map<String, String>> labelsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(storage).mergeVersionLabels(
                eq("orders"), eq("OrderEvent"), eq("1"),
                eq("field-tag.contract-1:"), labelsCaptor.capture());
        verify(storage, never()).mergeVersionLabels(
                eq("shared"), eq("Address"), any(), any(), any());

        Map<String, String> labels = labelsCaptor.getValue();
        assertTrue(labels.containsKey("field-tag.contract-1:orderId|PII"));
        assertFalse(labels.keySet().stream().anyMatch(key -> key.contains("street")));
    }

    @Test
    void projectReturnsZeroWhenNoSchemaEntryMatchesTarget() {
        OdcsSchema order = OdcsSchema.builder()
                .name("OrderEvent")
                .location("orders/OrderEvent")
                .fields(Map.of("orderId", OdcsFieldMetadata.builder().pii(true).build()))
                .build();
        OdcsContract contract = OdcsContract.builder()
                .schemas(List.of(order))
                .build();

        int count = projector.project(contract, "contract-1", "shared", "Address", new ArrayList<>());

        assertEquals(0, count);
        verify(storage, never()).mergeVersionLabels(any(), any(), any(), any(), any());
    }

    @Test
    void projectUsesVersionFromMatchingSchemaNotFirstEntry() {
        when(storage.getArtifactVersionMetaData("shared", "Address", "2")).thenReturn(null);
        when(storage.getArtifactVersions("shared", "Address")).thenReturn(List.of("2"));

        OdcsSchema order = OdcsSchema.builder()
                .name("OrderEvent")
                .location("orders/OrderEvent:99")
                .fields(Map.of("orderId", OdcsFieldMetadata.builder().pii(true).build()))
                .build();
        OdcsSchema address = OdcsSchema.builder()
                .name("Address")
                .location("shared/Address:2")
                .fields(new LinkedHashMap<>(Map.of(
                        "street", OdcsFieldMetadata.builder().tags(List.of("SENSITIVE")).build())))
                .build();
        OdcsContract contract = OdcsContract.builder()
                .schemas(List.of(order, address))
                .build();

        int count = projector.project(contract, "contract-1", "shared", "Address", new ArrayList<>());

        assertEquals(1, count);
        verify(storage).mergeVersionLabels(
                eq("shared"), eq("Address"), eq("2"),
                eq("field-tag.contract-1:"), any());
        verify(storage, never()).mergeVersionLabels(
                eq("orders"), eq("OrderEvent"), any(), any(), any());
    }
}
