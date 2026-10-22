package io.apicurio.schema.validation.protobuf;

import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.ParsedSchemaImpl;
import io.apicurio.registry.resolver.data.Record;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.protobuf.schema.ProtobufSchema;
import io.apicurio.schema.validation.SchemaValidationRecord;
import io.apicurio.schema.validation.ValidationResult;
import io.apicurio.schema.validation.protobuf.ref.MessageExample2OuterClass.MessageExample2;
import io.apicurio.schema.validation.protobuf.ref.MessageExampleOuterClass.MessageExample;
import io.apicurio.schema.validation.protobuf.ref.AddressOuterClass.Address;
import io.apicurio.schema.validation.protobuf.ref.PersonWithAddressOuterClass.PersonWithAddress;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ProtobufValidatorTest {

    @Test
    public void testValidMessage() {
        ProtobufValidator validator = new ProtobufValidator();

        MessageExample messageExample = MessageExample.newBuilder()
                .setKey("testValidMessageKey")
                .setValue("testValidMessageValue").build();
        ProtobufSchemaParser<MessageExample> protobufSchemaParser = new ProtobufSchemaParser<>();

        final byte[] schemaBytes = readResource("message_example.proto");
        final ProtobufSchema protobufSchema = protobufSchemaParser.parseSchema(schemaBytes, Collections.emptyMap());

        ParsedSchemaImpl<ProtobufSchema> ps = new ParsedSchemaImpl<ProtobufSchema>().setParsedSchema(
                protobufSchema).setRawSchema(schemaBytes);

        Record protobufRecord = new SchemaValidationRecord<>(messageExample, null);

        final ValidationResult result = validator.validate(ps, protobufRecord);

        assertTrue(result.success());
    }

    @Test
    public void testInvalidMessage() {
        ProtobufValidator validator = new ProtobufValidator();

        MessageExample2 messageExample = MessageExample2.newBuilder()
                .setKey2("testValidMessageKey")
                .setValue2(23).build();

        ProtobufSchemaParser<MessageExample> protobufSchemaParser = new ProtobufSchemaParser<>();
        final byte[] schemaBytes = readResource("message_example.proto");

        final ProtobufSchema protobufSchema = protobufSchemaParser.parseSchema(schemaBytes, Collections.emptyMap());
        ParsedSchemaImpl<ProtobufSchema> ps = new ParsedSchemaImpl<ProtobufSchema>().setParsedSchema(
                protobufSchema).setRawSchema(schemaBytes);

        Record protobufRecord = new SchemaValidationRecord<>(messageExample, null);

        final ValidationResult result = validator.validate(ps, protobufRecord);

        assertFalse(result.success());
        assertNotNull(result.getValidationErrors());
    }

    @Test
    public void testValidMessageWithReferences() {
        ProtobufValidator validator = new ProtobufValidator();
        ProtobufSchemaParser<PersonWithAddress> personParser = new ProtobufSchemaParser<>();
        ProtobufSchemaParser<Address> addressParser = new ProtobufSchemaParser<>();

        // Parse the referenced address schema
        final byte[] addressBytes = readResource("address.proto");
        final ProtobufSchema addressSchema = addressParser.parseSchema(addressBytes, Collections.emptyMap());
        ParsedSchemaImpl<ProtobufSchema> addressParsedSchema = new ParsedSchemaImpl<ProtobufSchema>()
                .setParsedSchema(addressSchema)
                .setRawSchema(addressBytes)
                .setReferenceName("address.proto");

        // Parse the main schema with the address reference
        final byte[] personBytes = readResource("person_with_address.proto");
        Map<String, ParsedSchema<ProtobufSchema>> refs = Map.of("address.proto", addressParsedSchema);
        final ProtobufSchema personSchema = personParser.parseSchema(personBytes, refs);

        ParsedSchemaImpl<ProtobufSchema> ps = new ParsedSchemaImpl<ProtobufSchema>()
                .setParsedSchema(personSchema)
                .setRawSchema(personBytes);

        // Create a valid PersonWithAddress message
        Address address = Address.newBuilder()
                .setStreet("123 Main St")
                .setCity("Springfield")
                .build();
        PersonWithAddress person = PersonWithAddress.newBuilder()
                .setName("John Doe")
                .setAddress(address)
                .build();

        Record protobufRecord = new SchemaValidationRecord<>(person, null);
        final ValidationResult result = validator.validate(ps, protobufRecord);

        assertTrue(result.success());
    }

    @Test
    public void testInvalidMessageWithReferences() {
        ProtobufValidator validator = new ProtobufValidator();
        ProtobufSchemaParser<PersonWithAddress> personParser = new ProtobufSchemaParser<>();
        ProtobufSchemaParser<Address> addressParser = new ProtobufSchemaParser<>();

        // Parse the referenced address schema
        final byte[] addressBytes = readResource("address.proto");
        final ProtobufSchema addressSchema = addressParser.parseSchema(addressBytes, Collections.emptyMap());
        ParsedSchemaImpl<ProtobufSchema> addressParsedSchema = new ParsedSchemaImpl<ProtobufSchema>()
                .setParsedSchema(addressSchema)
                .setRawSchema(addressBytes)
                .setReferenceName("address.proto");

        // Parse the main schema with the address reference
        final byte[] personBytes = readResource("person_with_address.proto");
        Map<String, ParsedSchema<ProtobufSchema>> refs = Map.of("address.proto", addressParsedSchema);
        final ProtobufSchema personSchema = personParser.parseSchema(personBytes, refs);

        ParsedSchemaImpl<ProtobufSchema> ps = new ParsedSchemaImpl<ProtobufSchema>()
                .setParsedSchema(personSchema)
                .setRawSchema(personBytes);

        // Use a completely different message type (MessageExample2), which should fail validation
        MessageExample2 wrongMessage = MessageExample2.newBuilder()
                .setKey2("testKey")
                .setValue2(42)
                .build();

        Record protobufRecord = new SchemaValidationRecord<>(wrongMessage, null);
        final ValidationResult result = validator.validate(ps, protobufRecord);

        assertFalse(result.success());
        assertNotNull(result.getValidationErrors());
    }

    public static byte[] readResource(String resourceName) {
        try (InputStream stream = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(resourceName)) {
            Assertions.assertNotNull(stream, "Resource not found: " + resourceName);
            return IoUtil.toBytes(stream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}