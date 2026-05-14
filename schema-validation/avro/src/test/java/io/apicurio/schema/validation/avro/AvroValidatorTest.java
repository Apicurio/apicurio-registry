/*
 * Copyright 2026 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.apicurio.schema.validation.avro;

import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.ParsedSchemaImpl;
import io.apicurio.registry.utils.IoUtil;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class AvroValidatorTest {

    @Test
    public void testValidRecord() {
        AvroValidator validator = new AvroValidator();

        Schema schema = loadSchema("message.avsc");
        GenericRecord record = createTestRecord(schema);

        var result = validator.validate(schema, record);

        assertTrue(result.success());
        assertNull(result.getValidationErrors());
    }

    @Test
    public void testInvalidRecordMissingField() {
        AvroValidator validator = new AvroValidator();

        Schema validSchema = loadSchema("message.avsc");
        Schema invalidSchema = loadSchema("message-invalid.avsc");

        GenericRecord record = createTestRecord(validSchema);

        var result = validator.validate(invalidSchema, record);

        assertFalse(result.success());
        assertNotNull(result.getValidationErrors());
        assertFalse(result.getValidationErrors().isEmpty());
        assertEquals(1, result.getValidationErrors().size());
    }

    @Test
    public void testValidJson() {
        AvroValidator validator = new AvroValidator();

        Schema schema = loadSchema("message.avsc");
        String json = "{\"message\": \"hello\", \"time\": 12345}";

        var result = validator.validateJson(schema, json);

        assertTrue(result.success());
    }

    @Test
    public void testInvalidJson() {
        AvroValidator validator = new AvroValidator();

        Schema schema = loadSchema("message.avsc");
        String json = "{\"message\": \"hello\", \"time\": \"not_a_number\"}";

        var result = validator.validateJson(schema, json);

        assertFalse(result.success());
        assertNotNull(result.getValidationErrors());
        assertFalse(result.getValidationErrors().isEmpty());
    }

    @Test
    public void testValidRecordWithReferences() {
        AvroValidator validator = new AvroValidator();
        AvroSchemaParser parser = new AvroSchemaParser();

        // Parse the referenced schema first
        byte[] addressBytes = readResourceBytes("address.avsc");
        Schema addressSchema = new Schema.Parser().parse(IoUtil.toString(addressBytes));
        ParsedSchemaImpl<Schema> addressParsedSchema = new ParsedSchemaImpl<Schema>()
                .setParsedSchema(addressSchema)
                .setRawSchema(addressBytes)
                .setReferenceName("address.avsc");

        // Parse the main schema with the reference
        byte[] mainBytes = readResourceBytes("message-with-ref.avsc");
        Map<String, ParsedSchema<Schema>> refs = Map.of("address.avsc", addressParsedSchema);
        Schema mainSchema = parser.parseSchema(mainBytes, refs);

        // Create a record with a nested Address
        GenericRecord addressRecord = new GenericData.Record(addressSchema);
        addressRecord.put("street", "123 Main St");
        addressRecord.put("city", "Springfield");

        GenericRecord record = new GenericData.Record(mainSchema);
        record.put("message", "hello");
        record.put("address", addressRecord);

        var result = validator.validate(mainSchema, record);

        assertTrue(result.success());
        assertNull(result.getValidationErrors());
    }

    @Test
    public void testInvalidRecordWithReferences() {
        AvroValidator validator = new AvroValidator();
        AvroSchemaParser parser = new AvroSchemaParser();

        // Parse the referenced schema first
        byte[] addressBytes = readResourceBytes("address.avsc");
        Schema addressSchema = new Schema.Parser().parse(IoUtil.toString(addressBytes));
        ParsedSchemaImpl<Schema> addressParsedSchema = new ParsedSchemaImpl<Schema>()
                .setParsedSchema(addressSchema)
                .setRawSchema(addressBytes)
                .setReferenceName("address.avsc");

        // Parse the main schema with the reference
        byte[] mainBytes = readResourceBytes("message-with-ref.avsc");
        Map<String, ParsedSchema<Schema>> refs = Map.of("address.avsc", addressParsedSchema);
        Schema mainSchema = parser.parseSchema(mainBytes, refs);

        // Validate with a schema that requires an extra field (message-invalid.avsc)
        // The record is missing the "address" field
        Schema invalidSchema = loadSchema("message-invalid.avsc");
        GenericRecord record = new GenericData.Record(mainSchema);
        record.put("message", "hello");
        record.put("address", null);

        var result = validator.validate(mainSchema, record);

        // address field is not nullable, so setting null should fail
        assertFalse(result.success());
        assertNotNull(result.getValidationErrors());
    }

    private GenericRecord createTestRecord(Schema schema) {
        GenericRecord record = new GenericData.Record(schema);
        record.put("message", "hello");
        record.put("time", System.currentTimeMillis());
        return record;
    }

    private Schema loadSchema(String resource) {
        return new Schema.Parser().parse(readResource(resource));
    }

    public static String readResource(String resourceName) {
        try (InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName)) {
            Assertions.assertNotNull(stream, "Resource not found: " + resourceName);
            return IoUtil.toString(stream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static byte[] readResourceBytes(String resourceName) {
        try (InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName)) {
            Assertions.assertNotNull(stream, "Resource not found: " + resourceName);
            return IoUtil.toBytes(stream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
