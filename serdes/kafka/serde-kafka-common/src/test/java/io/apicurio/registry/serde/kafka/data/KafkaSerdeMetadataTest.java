package io.apicurio.registry.serde.kafka.data;

import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class KafkaSerdeMetadataTest {

    @Test
    void testExplicitSchemaContentDefaultsToNull() {
        KafkaSerdeMetadata metadata = new KafkaSerdeMetadata("test-topic", false, new RecordHeaders());

        assertNull(metadata.explicitSchemaContent());
    }

    @Test
    void testExplicitSchemaTypeDefaultsToNull() {
        KafkaSerdeMetadata metadata = new KafkaSerdeMetadata("test-topic", false, new RecordHeaders());

        assertNull(metadata.explicitSchemaType());
    }

    @Test
    void testSetAndGetExplicitSchemaContent() {
        String schemaContent = "{\"type\": \"record\", \"name\": \"Test\", \"fields\": []}";
        KafkaSerdeMetadata metadata = new KafkaSerdeMetadata("test-topic", false, new RecordHeaders());

        metadata.setExplicitSchemaContent(schemaContent);

        assertEquals(schemaContent, metadata.explicitSchemaContent());
    }

    @Test
    void testSetAndGetExplicitSchemaType() {
        String schemaType = "AVRO";
        KafkaSerdeMetadata metadata = new KafkaSerdeMetadata("test-topic", false, new RecordHeaders());

        metadata.setExplicitSchemaType(schemaType);

        assertEquals(schemaType, metadata.explicitSchemaType());
    }

    @Test
    void testSetBothExplicitSchemaAndType() {
        String schemaContent = "{\"type\": \"string\"}";
        String schemaType = "JSON";
        KafkaSerdeMetadata metadata = new KafkaSerdeMetadata("test-topic", true, new RecordHeaders());

        metadata.setExplicitSchemaContent(schemaContent);
        metadata.setExplicitSchemaType(schemaType);

        assertEquals(schemaContent, metadata.explicitSchemaContent());
        assertEquals(schemaType, metadata.explicitSchemaType());
    }
}
