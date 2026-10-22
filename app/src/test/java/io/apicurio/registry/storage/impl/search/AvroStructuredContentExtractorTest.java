package io.apicurio.registry.storage.impl.search;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.apicurio.registry.avro.content.extract.AvroStructuredContentExtractor;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.extract.StructuredElement;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AvroStructuredContentExtractorTest {

    private final AvroStructuredContentExtractor extractor = new AvroStructuredContentExtractor();

    @Test
    void testExtractRecordNameAndNamespace() {
        String avroSchema = """
                {
                  "type": "record",
                  "name": "User",
                  "namespace": "com.example.events",
                  "fields": [
                    { "name": "id", "type": "long" },
                    { "name": "email", "type": "string" }
                  ]
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(avroSchema));

        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("name") && e.name().equals("User")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("namespace") && e.name().equals("com.example.events")));
    }

    @Test
    void testExtractFields() {
        String avroSchema = """
                {
                  "type": "record",
                  "name": "Order",
                  "fields": [
                    { "name": "orderId", "type": "string" },
                    { "name": "customerId", "type": "string" },
                    { "name": "amount", "type": "double" }
                  ]
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(avroSchema));

        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("field") && e.name().equals("orderId")));
        assertTrue(
                elements.stream().anyMatch(e -> e.kind().equals("field") && e.name().equals("customerId")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("field") && e.name().equals("amount")));
    }

    @Test
    void testExtractNestedRecordFields() {
        String avroSchema = """
                {
                  "type": "record",
                  "name": "Customer",
                  "fields": [
                    { "name": "name", "type": "string" },
                    {
                      "name": "address",
                      "type": {
                        "type": "record",
                        "name": "Address",
                        "fields": [
                          { "name": "street", "type": "string" },
                          { "name": "city", "type": "string" }
                        ]
                      }
                    }
                  ]
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(avroSchema));

        // Top-level record
        assertTrue(
                elements.stream().anyMatch(e -> e.kind().equals("name") && e.name().equals("Customer")));
        // Nested record
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("name") && e.name().equals("Address")));
        // Fields from both records
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("field") && e.name().equals("name")));
        assertTrue(
                elements.stream().anyMatch(e -> e.kind().equals("field") && e.name().equals("address")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("field") && e.name().equals("street")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("field") && e.name().equals("city")));
    }

    @Test
    void testExtractEnum() {
        String avroSchema = """
                {
                  "type": "enum",
                  "name": "Status",
                  "namespace": "com.example",
                  "symbols": ["ACTIVE", "INACTIVE", "PENDING"]
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(avroSchema));

        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("type") && e.name().equals("Status")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("namespace") && e.name().equals("com.example")));
        assertTrue(
                elements.stream().anyMatch(e -> e.kind().equals("enum_symbol") && e.name().equals("ACTIVE")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("enum_symbol") && e.name().equals("INACTIVE")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("enum_symbol") && e.name().equals("PENDING")));
    }

    @Test
    void testExtractRecordWithEnumField() {
        String avroSchema = """
                {
                  "type": "record",
                  "name": "Event",
                  "fields": [
                    { "name": "id", "type": "long" },
                    {
                      "name": "priority",
                      "type": {
                        "type": "enum",
                        "name": "Priority",
                        "symbols": ["LOW", "MEDIUM", "HIGH"]
                      }
                    }
                  ]
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(avroSchema));

        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("name") && e.name().equals("Event")));
        assertTrue(
                elements.stream().anyMatch(e -> e.kind().equals("type") && e.name().equals("Priority")));
        assertTrue(
                elements.stream().anyMatch(e -> e.kind().equals("enum_symbol") && e.name().equals("HIGH")));
        assertTrue(
                elements.stream().anyMatch(e -> e.kind().equals("field") && e.name().equals("priority")));
    }

    @Test
    void testExtractArrayAndMapTypes() {
        String avroSchema = """
                {
                  "type": "record",
                  "name": "Container",
                  "fields": [
                    { "name": "tags", "type": { "type": "array", "items": "string" } },
                    { "name": "metadata", "type": { "type": "map", "values": "string" } }
                  ]
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(avroSchema));

        assertTrue(
                elements.stream().anyMatch(e -> e.kind().equals("name") && e.name().equals("Container")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("field") && e.name().equals("tags")));
        assertTrue(
                elements.stream().anyMatch(e -> e.kind().equals("field") && e.name().equals("metadata")));
    }

    @Test
    void testExtractUnionType() {
        String avroSchema = """
                {
                  "type": "record",
                  "name": "Message",
                  "fields": [
                    {
                      "name": "payload",
                      "type": [
                        "null",
                        {
                          "type": "record",
                          "name": "TextPayload",
                          "fields": [{ "name": "text", "type": "string" }]
                        }
                      ]
                    }
                  ]
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(avroSchema));

        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("name") && e.name().equals("Message")));
        assertTrue(
                elements.stream().anyMatch(e -> e.kind().equals("name") && e.name().equals("TextPayload")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("field") && e.name().equals("text")));
    }

    @Test
    void testExtractFromInvalidContent() {
        List<StructuredElement> elements = extractor.extract(ContentHandle.create("not valid avro"));

        assertTrue(elements.isEmpty());
    }
}
