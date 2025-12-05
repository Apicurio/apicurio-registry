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
 * Tests for XSD compatibility checking
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

    private static final String BACKWARD_INCOMPATIBLE_SCHEMA_REMOVE_ELEMENT = """
<?xml version="1.0" encoding="UTF-8"?>
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
    <xs:element name="person" type="PersonType"/>
    <xs:complexType name="PersonType">
        <xs:sequence>
            <xs:element name="age" type="xs:int" minOccurs="0"/>
        </xs:sequence>
        <xs:attribute name="id" type="xs:string" use="required"/>
    </xs:complexType>
</xs:schema>
""";

    private static final String BACKWARD_INCOMPATIBLE_SCHEMA_INCREASE_MINOCCURS = """
<?xml version="1.0" encoding="UTF-8"?>
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
    <xs:element name="person" type="PersonType"/>
    <xs:complexType name="PersonType">
        <xs:sequence>
            <xs:element name="name" type="xs:string"/>
            <xs:element name="age" type="xs:int" minOccurs="1"/>
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

    private static final String SCHEMA_WITH_ENUM = """
<?xml version="1.0" encoding="UTF-8"?>
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
    <xs:element name="status" type="StatusType"/>
    <xs:simpleType name="StatusType">
        <xs:restriction base="xs:string">
            <xs:enumeration value="ACTIVE"/>
            <xs:enumeration value="INACTIVE"/>
            <xs:enumeration value="PENDING"/>
        </xs:restriction>
    </xs:simpleType>
</xs:schema>
""";

    private static final String SCHEMA_WITH_ENUM_VALUE_REMOVED = """
<?xml version="1.0" encoding="UTF-8"?>
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
    <xs:element name="status" type="StatusType"/>
    <xs:simpleType name="StatusType">
        <xs:restriction base="xs:string">
            <xs:enumeration value="ACTIVE"/>
            <xs:enumeration value="INACTIVE"/>
        </xs:restriction>
    </xs:simpleType>
</xs:schema>
""";

    private static final String SCHEMA_WITH_ENUM_VALUE_ADDED = """
<?xml version="1.0" encoding="UTF-8"?>
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
    <xs:element name="status" type="StatusType"/>
    <xs:simpleType name="StatusType">
        <xs:restriction base="xs:string">
            <xs:enumeration value="ACTIVE"/>
            <xs:enumeration value="INACTIVE"/>
            <xs:enumeration value="PENDING"/>
            <xs:enumeration value="COMPLETED"/>
        </xs:restriction>
    </xs:simpleType>
</xs:schema>
""";

    private TypedContent toTypedContent(String content) {
        return TypedContent.create(ContentHandle.create(content), ContentTypes.APPLICATION_XML);
    }

    @Test
    void testBackwardCompatible_AddOptionalElement() {
        XsdCompatibilityChecker checker = new XsdCompatibilityChecker();
        TypedContent existing = toTypedContent(BASE_SCHEMA);
        TypedContent proposed = toTypedContent(BACKWARD_COMPATIBLE_SCHEMA);
        
        CompatibilityExecutionResult result = checker.testCompatibility(
            CompatibilityLevel.BACKWARD,
            Collections.singletonList(existing),
            proposed,
            Collections.emptyMap()
        );

        Assertions.assertTrue(result.isCompatible(), 
            "Adding optional elements and attributes should be backward compatible");
    }

    @Test
    void testBackwardIncompatible_AddRequiredElement() {
        XsdCompatibilityChecker checker = new XsdCompatibilityChecker();
        TypedContent existing = toTypedContent(BASE_SCHEMA);
        TypedContent proposed = toTypedContent(BACKWARD_INCOMPATIBLE_SCHEMA_ADD_REQUIRED);
        
        CompatibilityExecutionResult result = checker.testCompatibility(
            CompatibilityLevel.BACKWARD,
            Collections.singletonList(existing),
            proposed,
            Collections.emptyMap()
        );

        Assertions.assertFalse(result.isCompatible(),
            "Adding required element should be backward incompatible");
        Assertions.assertFalse(result.getIncompatibleDifferences().isEmpty());
    }

    @Test
    void testBackwardIncompatible_RemoveRequiredElement() {
        XsdCompatibilityChecker checker = new XsdCompatibilityChecker();
        TypedContent existing = toTypedContent(BASE_SCHEMA);
        TypedContent proposed = toTypedContent(BACKWARD_INCOMPATIBLE_SCHEMA_REMOVE_ELEMENT);
        
        CompatibilityExecutionResult result = checker.testCompatibility(
            CompatibilityLevel.BACKWARD,
            Collections.singletonList(existing),
            proposed,
            Collections.emptyMap()
        );

        Assertions.assertFalse(result.isCompatible(),
            "Removing required element should be backward incompatible");
    }

    @Test
    void testBackwardIncompatible_IncreaseMinOccurs() {
        XsdCompatibilityChecker checker = new XsdCompatibilityChecker();
        TypedContent existing = toTypedContent(BASE_SCHEMA);
        TypedContent proposed = toTypedContent(BACKWARD_INCOMPATIBLE_SCHEMA_INCREASE_MINOCCURS);
        
        CompatibilityExecutionResult result = checker.testCompatibility(
            CompatibilityLevel.BACKWARD,
            Collections.singletonList(existing),
            proposed,
            Collections.emptyMap()
        );

        Assertions.assertFalse(result.isCompatible(),
            "Increasing minOccurs should be backward incompatible");
    }

    @Test
    void testBackwardIncompatible_TightenRestriction() {
        XsdCompatibilityChecker checker = new XsdCompatibilityChecker();
        TypedContent existing = toTypedContent(SCHEMA_WITH_RESTRICTION);
        TypedContent proposed = toTypedContent(SCHEMA_WITH_TIGHTER_RESTRICTION);
        
        CompatibilityExecutionResult result = checker.testCompatibility(
            CompatibilityLevel.BACKWARD,
            Collections.singletonList(existing),
            proposed,
            Collections.emptyMap()
        );

        Assertions.assertFalse(result.isCompatible(),
            "Tightening restrictions should be backward incompatible");
    }

    @Test
    void testBackwardCompatible_LoosenRestriction() {
        XsdCompatibilityChecker checker = new XsdCompatibilityChecker();
        TypedContent existing = toTypedContent(SCHEMA_WITH_RESTRICTION);
        TypedContent proposed = toTypedContent(SCHEMA_WITH_LOOSER_RESTRICTION);
        
        CompatibilityExecutionResult result = checker.testCompatibility(
            CompatibilityLevel.BACKWARD,
            Collections.singletonList(existing),
            proposed,
            Collections.emptyMap()
        );

        Assertions.assertTrue(result.isCompatible(),
            "Loosening restrictions should be backward compatible");
    }

    @Test
    void testBackwardIncompatible_RemoveEnumValue() {
        XsdCompatibilityChecker checker = new XsdCompatibilityChecker();
        TypedContent existing = toTypedContent(SCHEMA_WITH_ENUM);
        TypedContent proposed = toTypedContent(SCHEMA_WITH_ENUM_VALUE_REMOVED);
        
        CompatibilityExecutionResult result = checker.testCompatibility(
            CompatibilityLevel.BACKWARD,
            Collections.singletonList(existing),
            proposed,
            Collections.emptyMap()
        );

        Assertions.assertFalse(result.isCompatible(),
            "Removing enumeration values should be backward incompatible");
    }

    @Test
    void testBackwardCompatible_AddEnumValue() {
        XsdCompatibilityChecker checker = new XsdCompatibilityChecker();
        TypedContent existing = toTypedContent(SCHEMA_WITH_ENUM);
        TypedContent proposed = toTypedContent(SCHEMA_WITH_ENUM_VALUE_ADDED);
        
        CompatibilityExecutionResult result = checker.testCompatibility(
            CompatibilityLevel.BACKWARD,
            Collections.singletonList(existing),
            proposed,
            Collections.emptyMap()
        );

        Assertions.assertTrue(result.isCompatible(),
            "Adding enumeration values should be backward compatible");
    }

    @Test
    void testForwardCompatible_TightenRestriction() {
        XsdCompatibilityChecker checker = new XsdCompatibilityChecker();
        TypedContent existing = toTypedContent(SCHEMA_WITH_RESTRICTION);
        TypedContent proposed = toTypedContent(SCHEMA_WITH_TIGHTER_RESTRICTION);
        
        CompatibilityExecutionResult result = checker.testCompatibility(
            CompatibilityLevel.FORWARD,
            Collections.singletonList(existing),
            proposed,
            Collections.emptyMap()
        );

        Assertions.assertTrue(result.isCompatible(),
            "Tightening restrictions should be forward compatible");
    }

    @Test
    void testForwardIncompatible_LoosenRestriction() {
        XsdCompatibilityChecker checker = new XsdCompatibilityChecker();
        TypedContent existing = toTypedContent(SCHEMA_WITH_RESTRICTION);
        TypedContent proposed = toTypedContent(SCHEMA_WITH_LOOSER_RESTRICTION);
        
        CompatibilityExecutionResult result = checker.testCompatibility(
            CompatibilityLevel.FORWARD,
            Collections.singletonList(existing),
            proposed,
            Collections.emptyMap()
        );

        Assertions.assertFalse(result.isCompatible(),
            "Loosening restrictions should be forward incompatible");
    }

    @Test
    void testForwardIncompatible_AddOptionalElement() {
        XsdCompatibilityChecker checker = new XsdCompatibilityChecker();
        TypedContent existing = toTypedContent(BASE_SCHEMA);
        TypedContent proposed = toTypedContent(BACKWARD_COMPATIBLE_SCHEMA);
        
        CompatibilityExecutionResult result = checker.testCompatibility(
            CompatibilityLevel.FORWARD,
            Collections.singletonList(existing),
            proposed,
            Collections.emptyMap()
        );

        Assertions.assertFalse(result.isCompatible(),
            "Adding optional element should be forward incompatible (old schema won't accept new data with this element)");
    }

    @Test
    void testForwardCompatible_IncreaseMinOccurs() {
        XsdCompatibilityChecker checker = new XsdCompatibilityChecker();
        TypedContent existing = toTypedContent(BASE_SCHEMA);
        TypedContent proposed = toTypedContent(BACKWARD_INCOMPATIBLE_SCHEMA_INCREASE_MINOCCURS);
        
        CompatibilityExecutionResult result = checker.testCompatibility(
            CompatibilityLevel.FORWARD,
            Collections.singletonList(existing),
            proposed,
            Collections.emptyMap()
        );

        Assertions.assertTrue(result.isCompatible(),
            "Increasing minOccurs should be forward compatible (new data will always satisfy old constraints)");
    }

    @Test
    void testForwardCompatible_RemoveEnumValue() {
        XsdCompatibilityChecker checker = new XsdCompatibilityChecker();
        TypedContent existing = toTypedContent(SCHEMA_WITH_ENUM);
        TypedContent proposed = toTypedContent(SCHEMA_WITH_ENUM_VALUE_REMOVED);
        
        CompatibilityExecutionResult result = checker.testCompatibility(
            CompatibilityLevel.FORWARD,
            Collections.singletonList(existing),
            proposed,
            Collections.emptyMap()
        );

        Assertions.assertTrue(result.isCompatible(),
            "Removing enumeration values should be forward compatible (new data only uses remaining values)");
    }

    @Test
    void testForwardIncompatible_AddEnumValue() {
        XsdCompatibilityChecker checker = new XsdCompatibilityChecker();
        TypedContent existing = toTypedContent(SCHEMA_WITH_ENUM);
        TypedContent proposed = toTypedContent(SCHEMA_WITH_ENUM_VALUE_ADDED);
        
        CompatibilityExecutionResult result = checker.testCompatibility(
            CompatibilityLevel.FORWARD,
            Collections.singletonList(existing),
            proposed,
            Collections.emptyMap()
        );

        Assertions.assertFalse(result.isCompatible(),
            "Adding enumeration values should be forward incompatible (old schema won't accept new values)");
    }

    @Test
    void testFullCompatible() {
        XsdCompatibilityChecker checker = new XsdCompatibilityChecker();
        TypedContent existing = toTypedContent(BASE_SCHEMA);
        TypedContent proposed = toTypedContent(BASE_SCHEMA);
        
        CompatibilityExecutionResult result = checker.testCompatibility(
            CompatibilityLevel.FULL,
            Collections.singletonList(existing),
            proposed,
            Collections.emptyMap()
        );

        Assertions.assertTrue(result.isCompatible(),
            "Identical schemas should be fully compatible");
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
    void testFullCompatible_OnlyWithIdenticalSchema() {
        XsdCompatibilityChecker checker = new XsdCompatibilityChecker();
        TypedContent existing = toTypedContent(BASE_SCHEMA);
        TypedContent proposed = toTypedContent(BACKWARD_COMPATIBLE_SCHEMA);
        
        CompatibilityExecutionResult result = checker.testCompatibility(
            CompatibilityLevel.FULL,
            Collections.singletonList(existing),
            proposed,
            Collections.emptyMap()
        );

        Assertions.assertFalse(result.isCompatible(),
            "Adding optional element is backward compatible but not forward compatible, so not FULL compatible");
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
        TypedContent proposed = toTypedContent(BACKWARD_INCOMPATIBLE_SCHEMA_REMOVE_ELEMENT);
        
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
