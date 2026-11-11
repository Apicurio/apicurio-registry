package io.apicurio.registry.serde.avro;

import io.apicurio.registry.resolver.ParsedSchemaImpl;
import org.apache.avro.Schema;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AvroSchemaParserReferenceNamespaceTest {

    private final AvroSchemaParser<Object> parser = new AvroSchemaParser<>(null, 10);

    private String namespaceOf(String json) {
        Schema schema = new Schema.Parser().parse(json);
        return parser.referenceNamespace(new ParsedSchemaImpl<Schema>().setParsedSchema(schema)
                .setReferenceName(schema.getFullName()));
    }

    @Test
    void recordEnumAndFixedNamespaces() {
        assertEquals("com.acme", namespaceOf(
                "{\"type\":\"record\",\"namespace\":\"com.acme\",\"name\":\"Address\",\"fields\":[]}"));
        assertEquals("com.acme", namespaceOf(
                "{\"type\":\"enum\",\"namespace\":\"com.acme\",\"name\":\"State\",\"symbols\":[\"A\"]}"));
        assertEquals("com.acme", namespaceOf(
                "{\"type\":\"fixed\",\"namespace\":\"com.acme\",\"name\":\"Hash\",\"size\":16}"));
    }

    @Test
    void noNamespaceIsNull() {
        assertNull(namespaceOf("{\"type\":\"record\",\"name\":\"Address\",\"fields\":[]}"));
    }
}
