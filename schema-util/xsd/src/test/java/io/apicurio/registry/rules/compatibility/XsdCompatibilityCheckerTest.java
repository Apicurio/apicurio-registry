package io.apicurio.registry.rules.compatibility;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.xsd.rules.compatibility.XsdCompatibilityChecker;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

/**
 * Tests for XSD compatibility checking that cannot be expressed with the declarative JSON test data pattern
 * (see {@code compatibility-test-data.json} and {@link CompatibilityTestExecutor}).
 * <p>
 * These cases require multiple existing artifacts (transitive checks) or a specific compatibility level
 * (NONE), which the harness does not exercise.
 */
public class XsdCompatibilityCheckerTest {

    private static final String BASE_SCHEMA = """
<?xml version="1.0" encoding="UTF-8"?>
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
    <xs:element name="person" type="PersonType"/>
    <xs:complexType name="PersonType">
        <xs:sequence>
            <xs:element name="name" type="xs:string"/>
            <xs:element name="age" type="xs:int" minOccurs="0"/>
        </xs:sequence>
        <xs:attribute name="id" type="xs:string" use="required"/>
    </xs:complexType>
</xs:schema>
""";

    private static final String BACKWARD_COMPATIBLE_SCHEMA = """
<?xml version="1.0" encoding="UTF-8"?>
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
    <xs:element name="person" type="PersonType"/>
    <xs:complexType name="PersonType">
        <xs:sequence>
            <xs:element name="name" type="xs:string"/>
            <xs:element name="age" type="xs:int" minOccurs="0"/>
            <xs:element name="email" type="xs:string" minOccurs="0"/>
        </xs:sequence>
        <xs:attribute name="id" type="xs:string" use="required"/>
        <xs:attribute name="status" type="xs:string" use="optional"/>
    </xs:complexType>
</xs:schema>
""";

    private static final String BACKWARD_INCOMPATIBLE_SCHEMA_ADD_REQUIRED = """
<?xml version="1.0" encoding="UTF-8"?>
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
    <xs:element name="person" type="PersonType"/>
    <xs:complexType name="PersonType">
        <xs:sequence>
            <xs:element name="name" type="xs:string"/>
            <xs:element name="age" type="xs:int" minOccurs="0"/>
            <xs:element name="email" type="xs:string" minOccurs="1"/>
        </xs:sequence>
        <xs:attribute name="id" type="xs:string" use="required"/>
    </xs:complexType>
</xs:schema>
""";

    private static final String SCHEMA_WITH_RESTRICTION = """
<?xml version="1.0" encoding="UTF-8"?>
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
    <xs:element name="product" type="ProductType"/>
    <xs:simpleType name="PriceType">
        <xs:restriction base="xs:decimal">
            <xs:minInclusive value="0"/>
            <xs:maxInclusive value="1000"/>
        </xs:restriction>
    </xs:simpleType>
    <xs:complexType name="ProductType">
        <xs:sequence>
            <xs:element name="name" type="xs:string"/>
            <xs:element name="price" type="PriceType"/>
        </xs:sequence>
    </xs:complexType>
</xs:schema>
""";

    private static final String SCHEMA_WITH_TIGHTER_RESTRICTION = """
<?xml version="1.0" encoding="UTF-8"?>
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
    <xs:element name="product" type="ProductType"/>
    <xs:simpleType name="PriceType">
        <xs:restriction base="xs:decimal">
            <xs:minInclusive value="10"/>
            <xs:maxInclusive value="500"/>
        </xs:restriction>
    </xs:simpleType>
    <xs:complexType name="ProductType">
        <xs:sequence>
            <xs:element name="name" type="xs:string"/>
            <xs:element name="price" type="PriceType"/>
        </xs:sequence>
    </xs:complexType>
</xs:schema>
""";

    private static final String SCHEMA_WITH_LOOSER_RESTRICTION = """
<?xml version="1.0" encoding="UTF-8"?>
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
    <xs:element name="product" type="ProductType"/>
    <xs:simpleType name="PriceType">
        <xs:restriction base="xs:decimal">
            <xs:minInclusive value="0"/>
            <xs:maxInclusive value="2000"/>
        </xs:restriction>
    </xs:simpleType>
    <xs:complexType name="ProductType">
        <xs:sequence>
            <xs:element name="name" type="xs:string"/>
            <xs:element name="price" type="PriceType"/>
        </xs:sequence>
    </xs:complexType>
</xs:schema>
""";

    private TypedContent toTypedContent(String content) {
        return TypedContent.create(ContentHandle.create(content), ContentTypes.APPLICATION_XML);
    }

    @Test
    void testBackwardTransitive() {
        XsdCompatibilityChecker checker = new XsdCompatibilityChecker();
        TypedContent schema1 = toTypedContent(BASE_SCHEMA);
        TypedContent schema2 = toTypedContent(BACKWARD_COMPATIBLE_SCHEMA);
        TypedContent schema3 = toTypedContent(BACKWARD_INCOMPATIBLE_SCHEMA_ADD_REQUIRED);
        
        CompatibilityExecutionResult result = checker.testCompatibility(
            CompatibilityLevel.BACKWARD_TRANSITIVE,
            List.of(schema1, schema2),
            schema3,
            Collections.emptyMap()
        );

        Assertions.assertFalse(result.isCompatible(),
            "Schema 3 should be incompatible with schema 1 in transitive check");
    }

    @Test
    void testForwardTransitive() {
        XsdCompatibilityChecker checker = new XsdCompatibilityChecker();
        TypedContent schema1 = toTypedContent(SCHEMA_WITH_RESTRICTION);
        TypedContent schema2 = toTypedContent(SCHEMA_WITH_TIGHTER_RESTRICTION);
        TypedContent schema3 = toTypedContent(SCHEMA_WITH_LOOSER_RESTRICTION);
        
        CompatibilityExecutionResult result = checker.testCompatibility(
            CompatibilityLevel.FORWARD_TRANSITIVE,
            List.of(schema1, schema2),
            schema3,
            Collections.emptyMap()
        );

        Assertions.assertFalse(result.isCompatible(),
            "Schema 3 (looser) should be incompatible with schema 2 (tighter) in forward transitive check");
    }

    @Test
    void testFullTransitive() {
        XsdCompatibilityChecker checker = new XsdCompatibilityChecker();
        TypedContent schema1 = toTypedContent(BASE_SCHEMA);
        TypedContent schema2 = toTypedContent(BASE_SCHEMA);
        TypedContent schema3 = toTypedContent(BACKWARD_COMPATIBLE_SCHEMA);
        
        CompatibilityExecutionResult result = checker.testCompatibility(
            CompatibilityLevel.FULL_TRANSITIVE,
            List.of(schema1, schema2),
            schema3,
            Collections.emptyMap()
        );

        Assertions.assertFalse(result.isCompatible(),
            "Adding optional element is not full compatible");
    }

    @Test
    void testNoneCompatibility() {
        XsdCompatibilityChecker checker = new XsdCompatibilityChecker();
        TypedContent existing = toTypedContent(BASE_SCHEMA);
        TypedContent proposed = toTypedContent(BASE_SCHEMA);
        
        CompatibilityExecutionResult result = checker.testCompatibility(
            CompatibilityLevel.NONE,
            Collections.singletonList(existing),
            proposed,
            Collections.emptyMap()
        );

        Assertions.assertTrue(result.isCompatible(),
            "NONE compatibility level should always pass");
    }
}
