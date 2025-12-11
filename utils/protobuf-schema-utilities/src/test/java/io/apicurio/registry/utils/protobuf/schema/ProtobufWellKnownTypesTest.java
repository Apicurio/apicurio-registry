package io.apicurio.registry.utils.protobuf.schema;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ProtobufWellKnownTypes}.
 */
class ProtobufWellKnownTypesTest {

    // ============== isGoogleProtobufType tests ==============

    @Test
    void testIsGoogleProtobufType_withTimestamp() {
        assertTrue(ProtobufWellKnownTypes.isGoogleProtobufType("google/protobuf/timestamp.proto"));
    }

    @Test
    void testIsGoogleProtobufType_withDuration() {
        assertTrue(ProtobufWellKnownTypes.isGoogleProtobufType("google/protobuf/duration.proto"));
    }

    @Test
    void testIsGoogleProtobufType_withAny() {
        assertTrue(ProtobufWellKnownTypes.isGoogleProtobufType("google/protobuf/any.proto"));
    }

    @Test
    void testIsGoogleProtobufType_withGoogleType() {
        assertFalse(ProtobufWellKnownTypes.isGoogleProtobufType("google/type/money.proto"));
    }

    @Test
    void testIsGoogleProtobufType_withUserProto() {
        assertFalse(ProtobufWellKnownTypes.isGoogleProtobufType("mypackage/user.proto"));
    }

    @Test
    void testIsGoogleProtobufType_withNull() {
        assertFalse(ProtobufWellKnownTypes.isGoogleProtobufType(null));
    }

    // ============== isGoogleApiType tests ==============

    @Test
    void testIsGoogleApiType_withMoney() {
        assertTrue(ProtobufWellKnownTypes.isGoogleApiType("google/type/money.proto"));
    }

    @Test
    void testIsGoogleApiType_withDate() {
        assertTrue(ProtobufWellKnownTypes.isGoogleApiType("google/type/date.proto"));
    }

    @Test
    void testIsGoogleApiType_withGoogleProtobuf() {
        assertFalse(ProtobufWellKnownTypes.isGoogleApiType("google/protobuf/timestamp.proto"));
    }

    @Test
    void testIsGoogleApiType_withUserProto() {
        assertFalse(ProtobufWellKnownTypes.isGoogleApiType("mypackage/user.proto"));
    }

    @Test
    void testIsGoogleApiType_withNull() {
        assertFalse(ProtobufWellKnownTypes.isGoogleApiType(null));
    }

    // ============== isApicurioBundledType tests ==============

    @Test
    void testIsApicurioBundledType_withMetadata() {
        assertTrue(ProtobufWellKnownTypes.isApicurioBundledType("metadata/metadata.proto"));
    }

    @Test
    void testIsApicurioBundledType_withDecimal() {
        assertTrue(ProtobufWellKnownTypes.isApicurioBundledType("additionalTypes/decimal.proto"));
    }

    @Test
    void testIsApicurioBundledType_withGoogleProtobuf() {
        assertFalse(ProtobufWellKnownTypes.isApicurioBundledType("google/protobuf/timestamp.proto"));
    }

    @Test
    void testIsApicurioBundledType_withUserProto() {
        assertFalse(ProtobufWellKnownTypes.isApicurioBundledType("mypackage/user.proto"));
    }

    @Test
    void testIsApicurioBundledType_withNull() {
        assertFalse(ProtobufWellKnownTypes.isApicurioBundledType(null));
    }

    // ============== isWellKnownType tests ==============

    @Test
    void testIsWellKnownType_withGoogleProtobuf() {
        assertTrue(ProtobufWellKnownTypes.isWellKnownType("google/protobuf/timestamp.proto"));
    }

    @Test
    void testIsWellKnownType_withGoogleType() {
        assertTrue(ProtobufWellKnownTypes.isWellKnownType("google/type/money.proto"));
    }

    @Test
    void testIsWellKnownType_withMetadata() {
        assertTrue(ProtobufWellKnownTypes.isWellKnownType("metadata/metadata.proto"));
    }

    @Test
    void testIsWellKnownType_withAdditionalTypes() {
        assertTrue(ProtobufWellKnownTypes.isWellKnownType("additionalTypes/decimal.proto"));
    }

    @Test
    void testIsWellKnownType_withUserProto() {
        assertFalse(ProtobufWellKnownTypes.isWellKnownType("mypackage/user.proto"));
    }

    @Test
    void testIsWellKnownType_withNull() {
        assertFalse(ProtobufWellKnownTypes.isWellKnownType(null));
    }

    // ============== isHandledByProtobuf4j tests ==============

    @Test
    void testIsHandledByProtobuf4j_withGoogleProtobuf() {
        assertTrue(ProtobufWellKnownTypes.isHandledByProtobuf4j("google/protobuf/any.proto"));
    }

    @Test
    void testIsHandledByProtobuf4j_withGoogleType() {
        // Google API types are NOT handled by protobuf4j - they're loaded from resources
        assertFalse(ProtobufWellKnownTypes.isHandledByProtobuf4j("google/type/money.proto"));
    }

    @Test
    void testIsHandledByProtobuf4j_withUserProto() {
        assertFalse(ProtobufWellKnownTypes.isHandledByProtobuf4j("mypackage/user.proto"));
    }

    // ============== shouldSkipAsReference tests ==============

    @Test
    void testShouldSkipAsReference_withGoogleProtobuf() {
        assertTrue(ProtobufWellKnownTypes.shouldSkipAsReference("google/protobuf/timestamp.proto"));
    }

    @Test
    void testShouldSkipAsReference_withGoogleType() {
        assertTrue(ProtobufWellKnownTypes.shouldSkipAsReference("google/type/money.proto"));
    }

    @Test
    void testShouldSkipAsReference_withMetadata() {
        // Apicurio bundled types should NOT be skipped as references
        assertFalse(ProtobufWellKnownTypes.shouldSkipAsReference("metadata/metadata.proto"));
    }

    @Test
    void testShouldSkipAsReference_withUserProto() {
        assertFalse(ProtobufWellKnownTypes.shouldSkipAsReference("mypackage/user.proto"));
    }

    // ============== Package name tests ==============

    @Test
    void testIsGoogleProtobufPackage() {
        assertTrue(ProtobufWellKnownTypes.isGoogleProtobufPackage("google.protobuf"));
        assertFalse(ProtobufWellKnownTypes.isGoogleProtobufPackage("google.type"));
        assertFalse(ProtobufWellKnownTypes.isGoogleProtobufPackage("com.example"));
    }

    @Test
    void testIsGoogleTypePackage() {
        assertTrue(ProtobufWellKnownTypes.isGoogleTypePackage("google.type"));
        assertFalse(ProtobufWellKnownTypes.isGoogleTypePackage("google.protobuf"));
        assertFalse(ProtobufWellKnownTypes.isGoogleTypePackage("com.example"));
    }

    @Test
    void testIsApicurioBundledPackage() {
        assertTrue(ProtobufWellKnownTypes.isApicurioBundledPackage("metadata"));
        assertTrue(ProtobufWellKnownTypes.isApicurioBundledPackage("additionalTypes"));
        assertFalse(ProtobufWellKnownTypes.isApicurioBundledPackage("google.protobuf"));
        assertFalse(ProtobufWellKnownTypes.isApicurioBundledPackage("com.example"));
    }

    @Test
    void testGetMetadataPackage() {
        assertEquals("metadata", ProtobufWellKnownTypes.getMetadataPackage());
    }

    @Test
    void testGetAdditionalTypesPackage() {
        assertEquals("additionalTypes", ProtobufWellKnownTypes.getAdditionalTypesPackage());
    }

    // ============== isGoogleProtobufSchema tests ==============

    @Test
    void testIsGoogleProtobufSchema_withTimestampProto() {
        String schema = """
                syntax = "proto3";
                package google.protobuf;

                message Timestamp {
                    int64 seconds = 1;
                    int32 nanos = 2;
                }
                """;
        assertTrue(ProtobufWellKnownTypes.isGoogleProtobufSchema(schema));
    }

    @Test
    void testIsGoogleProtobufSchema_withUserProto() {
        String schema = """
                syntax = "proto3";
                package com.example;

                message User {
                    string name = 1;
                }
                """;
        assertFalse(ProtobufWellKnownTypes.isGoogleProtobufSchema(schema));
    }

    @Test
    void testIsGoogleProtobufSchema_withNull() {
        assertFalse(ProtobufWellKnownTypes.isGoogleProtobufSchema(null));
    }

    @Test
    void testIsGoogleProtobufSchema_withExtraWhitespace() {
        String schema = "syntax = \"proto3\";\n  package   google.protobuf  ;\nmessage Test {}";
        assertTrue(ProtobufWellKnownTypes.isGoogleProtobufSchema(schema));
    }

    @Test
    void testIsGoogleProtobufSchema_withGoogleTypePackage() {
        String schema = """
                syntax = "proto3";
                package google.type;

                message Money {
                    string currency_code = 1;
                }
                """;
        // google.type is NOT google.protobuf
        assertFalse(ProtobufWellKnownTypes.isGoogleProtobufSchema(schema));
    }
}
