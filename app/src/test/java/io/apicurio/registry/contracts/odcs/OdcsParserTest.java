package io.apicurio.registry.contracts.odcs;

import io.apicurio.registry.content.ContentHandle;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OdcsParserTest {

    private final OdcsParser parser = new OdcsParser();

    @Test
    void testParseValidContract() throws Exception {
        String yaml = loadResource("valid-contract.yaml");
        OdcsContract contract = parser.parse(yaml);

        assertNotNull(contract);
        assertEquals("v3.1.0", contract.getApiVersion());
        assertEquals("DataContract", contract.getKind());
        assertEquals("orders-contract", contract.getId());
    }

    @Test
    void testParseInfo() throws Exception {
        String yaml = loadResource("valid-contract.yaml");
        OdcsContract contract = parser.parse(yaml);

        OdcsInfo info = contract.getInfo();
        assertNotNull(info);
        assertEquals("Orders Contract", info.getTitle());
        assertEquals("1.0.0", info.getVersion());
        assertEquals("active", info.getStatus());
        assertEquals("confidential", info.getDataClassification());
    }

    @Test
    void testParseTeam() throws Exception {
        String yaml = loadResource("valid-contract.yaml");
        OdcsContract contract = parser.parse(yaml);

        OdcsTeam team = contract.getTeam();
        assertNotNull(team);
        assertEquals("orders-team", team.getName());
        assertEquals("commerce", team.getDomain());
        assertEquals("orders@company.com", team.getContact());
    }

    @Test
    void testParseSchemas() throws Exception {
        String yaml = loadResource("valid-contract.yaml");
        OdcsContract contract = parser.parse(yaml);

        assertNotNull(contract.getSchemas());
        assertEquals(1, contract.getSchemas().size());

        OdcsSchema schema = contract.getSchemas().get(0);
        assertEquals("OrderEvent", schema.getName());
        assertEquals("avro", schema.getType());
        assertEquals("orders/OrderEvent:3", schema.getLocation());

        assertNotNull(schema.getFields());
        assertEquals(3, schema.getFields().size());

        OdcsFieldMetadata emailField = schema.getFields().get("customerEmail");
        assertNotNull(emailField);
        assertTrue(Boolean.TRUE.equals(emailField.getPii()));
        assertEquals("confidential", emailField.getClassification());
        assertEquals(2, emailField.getTags().size());
        assertTrue(emailField.getTags().contains("PII"));
        assertTrue(emailField.getTags().contains("EMAIL"));
    }

    @Test
    void testParseQuality() throws Exception {
        String yaml = loadResource("valid-contract.yaml");
        OdcsContract contract = parser.parse(yaml);

        OdcsQuality quality = contract.getQuality();
        assertNotNull(quality);

        assertNotNull(quality.getFreshness());
        assertEquals("PT5M", quality.getFreshness().getMaxStaleness());

        assertNotNull(quality.getCompleteness());
        assertEquals(2, quality.getCompleteness().size());
        assertEquals("orderId", quality.getCompleteness().get(0).getField());
        assertEquals(1.0, quality.getCompleteness().get(0).getThreshold());

        assertNotNull(quality.getAccuracy());
        assertEquals(2, quality.getAccuracy().size());
        assertEquals("positive-amount", quality.getAccuracy().get(0).getName());
        assertEquals("totalAmount > 0", quality.getAccuracy().get(0).getExpression());
        assertEquals(1.0, quality.getAccuracy().get(0).getThreshold());
    }

    @Test
    void testParseServiceLevel() throws Exception {
        String yaml = loadResource("valid-contract.yaml");
        OdcsContract contract = parser.parse(yaml);

        OdcsServiceLevel sl = contract.getServiceLevel();
        assertNotNull(sl);
        assertEquals(0.999, sl.getAvailability());
        assertEquals("PT0.1S", sl.getLatency().getP50());
        assertEquals("PT1S", sl.getLatency().getP99());
        assertEquals(100, sl.getThroughput().getMinEventsPerMinute());
        assertEquals(100000, sl.getThroughput().getMaxEventsPerMinute());
    }

    @Test
    void testSerializeRoundTrip() throws Exception {
        String yaml = loadResource("valid-contract.yaml");
        OdcsContract contract = parser.parse(yaml);

        String serialized = parser.serialize(contract);
        assertNotNull(serialized);

        OdcsContract reparsed = parser.parse(serialized);
        assertEquals(contract.getApiVersion(), reparsed.getApiVersion());
        assertEquals(contract.getKind(), reparsed.getKind());
        assertEquals(contract.getInfo().getTitle(), reparsed.getInfo().getTitle());
        assertEquals(contract.getTeam().getName(), reparsed.getTeam().getName());
    }

    @Test
    void testParseInvalidYaml() {
        assertThrows(OdcsParseException.class,
                () -> parser.parse(ContentHandle.create("not: [valid: yaml: {{")));
    }

    @Test
    void testParseMinimalContract() {
        String yaml = """
                apiVersion: v3.1.0
                kind: DataContract
                info:
                  title: Minimal
                  version: 1.0.0
                """;

        OdcsContract contract = parser.parse(yaml);
        assertNotNull(contract);
        assertEquals("Minimal", contract.getInfo().getTitle());
    }

    @Test
    void testParseNonPiiField() throws Exception {
        String yaml = loadResource("valid-contract.yaml");
        OdcsContract contract = parser.parse(yaml);

        OdcsFieldMetadata orderIdField = contract.getSchemas().get(0).getFields()
                .get("orderId");
        assertNotNull(orderIdField);
        assertFalse(Boolean.TRUE.equals(orderIdField.getPii()));
        assertEquals("internal", orderIdField.getClassification());
    }

    private String loadResource(String name) throws Exception {
        try (InputStream is = getClass().getResourceAsStream(name)) {
            assertNotNull(is, "Resource not found: " + name);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
