package io.apicurio.registry.resolver.strategy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReferenceNamingStrategiesTest {

    private static ReferenceNamingContext ctx(String name, String type, String defaultGroup, String namespace) {
        return new ReferenceNamingContext(name, type, defaultGroup, "parent-group", "Parent", namespace, null);
    }

    @Test
    void defaultUsesDefaultGroupAndReferenceName() {
        ReferenceNamingStrategy s = new DefaultReferenceNamingStrategy();
        assertEquals(new ReferenceCoordinates("orders", "com.acme.Address"),
                s.name(ctx("com.acme.Address", "AVRO", "orders", "com.acme")));
        assertEquals(new ReferenceCoordinates(null, "acme/address.proto"),
                s.name(ctx("acme/address.proto", "PROTOBUF", null, "acme")));
    }

    @Test
    void namespaceGroupForAvroStripsNamespacePrefix() {
        ReferenceNamingStrategy s = new NamespaceGroupReferenceNamingStrategy();
        assertEquals(new ReferenceCoordinates("com.acme", "Address"),
                s.name(ctx("com.acme.Address", "AVRO", "orders", "com.acme")));
    }

    @Test
    void namespaceGroupForProtobufKeepsFilePath() {
        ReferenceNamingStrategy s = new NamespaceGroupReferenceNamingStrategy();
        assertEquals(new ReferenceCoordinates("acme.common", "acme/address.proto"),
                s.name(ctx("acme/address.proto", "PROTOBUF", "orders", "acme.common")));
    }

    @Test
    void namespaceGroupWithoutNamespaceFallsBackToDefaultGroup() {
        ReferenceNamingStrategy s = new NamespaceGroupReferenceNamingStrategy();
        assertEquals(new ReferenceCoordinates("orders", "Address"),
                s.name(ctx("Address", "AVRO", "orders", null)));
        assertEquals(new ReferenceCoordinates(null, "Address"),
                s.name(ctx("Address", "AVRO", null, "")));
    }

    @Test
    void namespaceGroupWithNamespaceThatIsNotAPrefixKeepsFullName() {
        ReferenceNamingStrategy s = new NamespaceGroupReferenceNamingStrategy();
        assertEquals(new ReferenceCoordinates("com.other", "com.acme.Address"),
                s.name(ctx("com.acme.Address", "AVRO", "orders", "com.other")));
        // "com.acmeX" must not be treated as being inside namespace "com.acme"
        assertEquals(new ReferenceCoordinates("com.acme", "com.acmeX"),
                s.name(ctx("com.acmeX", "AVRO", "orders", "com.acme")));
    }
}
