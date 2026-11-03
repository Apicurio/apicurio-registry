package io.apicurio.registry.rules.validity;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.violation.RuleViolation;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.xsd.rules.validity.XsdContentValidator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class XsdContentValidatorTest extends ArtifactUtilProviderTestBase {
    @Test
    public void testValidSyntax() throws Exception {
        TypedContent contentA = resourceToTypedContentHandle("xml-schema-valid.xsd");
        XsdContentValidator validator = new XsdContentValidator();
        validator.validate(ValidityLevel.SYNTAX_ONLY, contentA, Collections.emptyMap());
        TypedContent contentB = resourceToTypedContentHandle("xml-schema-invalid-semantics.xsd");
        validator.validate(ValidityLevel.SYNTAX_ONLY, contentB, Collections.emptyMap());
    }

    @Test
    public void testInvalidSyntax() throws Exception {
        TypedContent content = resourceToTypedContentHandle("xml-schema-invalid-syntax.xsd");
        XsdContentValidator validator = new XsdContentValidator();
        Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.SYNTAX_ONLY, content, Collections.emptyMap());
        });
    }

    @Test
    public void testValidSemantics() throws Exception {
        TypedContent content = resourceToTypedContentHandle("xml-schema-valid.xsd");
        XsdContentValidator validator = new XsdContentValidator();
        validator.validate(ValidityLevel.FULL, content, Collections.emptyMap());
    }

    @Test
    public void testInvalidSemantics() throws Exception {
        TypedContent content = resourceToTypedContentHandle("xml-schema-invalid-semantics.xsd");
        XsdContentValidator validator = new XsdContentValidator();
        Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.FULL, content, Collections.emptyMap());
        });
    }

    @Test
    public void testValidateReferencesAllMapped() {
        TypedContent content = resourceToTypedContentHandle("xsd-with-refs.xsd");
        List<ArtifactReference> refs = List.of(
                ArtifactReference.builder().name("common.xsd").groupId("g").artifactId("c").version("1").build(),
                ArtifactReference.builder().name("order-parts.xsd").groupId("g").artifactId("p").version("1").build());
        Assertions.assertDoesNotThrow(() -> new XsdContentValidator().validateReferences(content, refs));
    }

    @Test
    public void testValidateReferencesUnmapped() {
        TypedContent content = resourceToTypedContentHandle("xsd-with-refs.xsd");
        List<ArtifactReference> refs = List.of(
                ArtifactReference.builder().name("common.xsd").groupId("g").artifactId("c").version("1").build());
        RuleViolationException ex = Assertions.assertThrows(RuleViolationException.class,
                () -> new XsdContentValidator().validateReferences(content, refs));
        Assertions.assertEquals(RuleType.INTEGRITY, ex.getRuleType());
        Assertions.assertEquals(1, ex.getCauses().size());
        RuleViolation v = ex.getCauses().iterator().next();
        Assertions.assertEquals("order-parts.xsd", v.getContext());
        Assertions.assertEquals("Unmapped reference detected.", v.getDescription());
    }

    @Test
    public void testValidateReferencesMalformedIgnored() {
        TypedContent bad = TypedContent.create(ContentHandle.create("<xs:schema"), ContentTypes.APPLICATION_XML);
        Assertions.assertDoesNotThrow(() -> new XsdContentValidator().validateReferences(bad, List.of()));
    }

    private static final String COMMON_XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:common">
              <xs:complexType name="Address"><xs:sequence>
                <xs:element name="street" type="xs:string"/></xs:sequence></xs:complexType>
            </xs:schema>
            """;
    private static final String ORDER_XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:c="urn:common"
                       targetNamespace="urn:order">
              <xs:import namespace="urn:common" schemaLocation="common.xsd"/>
              <xs:element name="order" type="c:Address"/>
            </xs:schema>
            """;

    private static TypedContent xml(String s) {
        return TypedContent.create(ContentHandle.create(s), ContentTypes.APPLICATION_XML);
    }

    @Test
    public void testFullValidityWithMappedImport() {
        Assertions.assertDoesNotThrow(() -> new XsdContentValidator().validate(ValidityLevel.FULL,
                xml(ORDER_XSD), Map.of("common.xsd", xml(COMMON_XSD))));
    }

    @Test
    public void testFullValidityWithUnmappedImport() {
        RuleViolationException ex = Assertions.assertThrows(RuleViolationException.class,
                () -> new XsdContentValidator().validate(ValidityLevel.FULL, xml(ORDER_XSD), Map.of()));
        Assertions.assertEquals(RuleType.VALIDITY, ex.getRuleType());
        Assertions.assertEquals(1, ex.getCauses().size());
        RuleViolation cause = ex.getCauses().iterator().next();
        Assertions.assertEquals("common.xsd", cause.getContext());
        Assertions.assertEquals("Unresolved reference: common.xsd", cause.getDescription());
    }
}
