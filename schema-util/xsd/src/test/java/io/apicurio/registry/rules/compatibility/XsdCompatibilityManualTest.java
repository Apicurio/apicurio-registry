package io.apicurio.registry.rules.compatibility;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.types.ContentTypes;

import java.util.Collections;

/**
 * Manual test to verify XSD compatibility checking is working
 */
public class XsdCompatibilityManualTest {

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

    private static final String NEW_SCHEMA_WITH_REQUIRED_ELEMENT = """
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

    public static void main(String[] args) {
        XsdCompatibilityChecker checker = new XsdCompatibilityChecker();
        
        TypedContent existing = TypedContent.create(
            ContentHandle.create(BASE_SCHEMA), 
            ContentTypes.APPLICATION_XML
        );
        
        TypedContent proposed = TypedContent.create(
            ContentHandle.create(NEW_SCHEMA_WITH_REQUIRED_ELEMENT), 
            ContentTypes.APPLICATION_XML
        );
        
        System.out.println("Testing backward compatibility when adding a required element...");
        System.out.println("Existing schema has: name (required), age (optional)");
        System.out.println("Proposed schema has: name (required), age (optional), email (REQUIRED - NEW)");
        System.out.println();
        
        CompatibilityExecutionResult result = checker.testCompatibility(
            CompatibilityLevel.BACKWARD,
            Collections.singletonList(existing),
            proposed,
            Collections.emptyMap()
        );
        
        System.out.println("Is compatible: " + result.isCompatible());
        System.out.println("Expected: false (should be INCOMPATIBLE)");
        System.out.println();
        
        if (!result.isCompatible()) {
            System.out.println("✅ CORRECT! Adding a required element is detected as backward incompatible");
            System.out.println("\nIncompatibilities found:");
            result.getIncompatibleDifferences().forEach(diff -> {
                System.out.println("  - " + diff.asRuleViolation());
            });
        } else {
            System.out.println("❌ ERROR! Should have detected incompatibility");
        }
    }
}
