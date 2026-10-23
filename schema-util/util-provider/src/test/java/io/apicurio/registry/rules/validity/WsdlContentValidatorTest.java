package io.apicurio.registry.rules.validity;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.violation.RuleViolation;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.wsdl.rules.validity.WsdlContentValidator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class WsdlContentValidatorTest extends ArtifactUtilProviderTestBase {

    private static final String COMMON = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:common">
              <xs:complexType name="Address"><xs:sequence>
                <xs:element name="street" type="xs:string"/></xs:sequence></xs:complexType>
            </xs:schema>
            """;

    @Test
    public void testFullEmbeddedImportMapped() {
        TypedContent content = resourceToTypedContentHandle("wsdl-embedded-import.wsdl");
        TypedContent common = TypedContent.create(ContentHandle.create(COMMON), ContentTypes.APPLICATION_XML);
        Assertions.assertDoesNotThrow(() -> new WsdlContentValidator().validate(ValidityLevel.FULL, content,
                Map.of("common.xsd", common)));
    }

    @Test
    public void testFullEmbeddedImportUnmapped() {
        TypedContent content = resourceToTypedContentHandle("wsdl-embedded-import.wsdl");
        RuleViolationException ex = Assertions.assertThrows(RuleViolationException.class,
                () -> new WsdlContentValidator().validate(ValidityLevel.FULL, content, Map.of()));
        Assertions.assertEquals(RuleType.VALIDITY, ex.getRuleType());
        Assertions.assertEquals(1, ex.getCauses().size());
        RuleViolation v = ex.getCauses().iterator().next();
        Assertions.assertEquals("common.xsd", v.getContext());
        Assertions.assertEquals("Unresolved reference: common.xsd", v.getDescription());
    }

    @Test
    public void testFullTwoInlineSchemasCrossReference() {
        TypedContent content = resourceToTypedContentHandle("wsdl-two-inline-schemas.wsdl");
        Assertions.assertDoesNotThrow(() -> new WsdlContentValidator().validate(ValidityLevel.FULL, content,
                Map.of()));
    }
    @Test
    public void testValidSyntax() throws Exception {
        TypedContent contentA = resourceToTypedContentHandle("wsdl-valid.wsdl");
        WsdlContentValidator validator = new WsdlContentValidator();
        validator.validate(ValidityLevel.SYNTAX_ONLY, contentA, Collections.emptyMap());
        TypedContent contentB = resourceToTypedContentHandle("wsdl-invalid-semantics.wsdl");
        validator.validate(ValidityLevel.SYNTAX_ONLY, contentB, Collections.emptyMap());
    }

    @Test
    public void testinValidSyntax() throws Exception {
        TypedContent content = resourceToTypedContentHandle("wsdl-invalid-syntax.wsdl");
        WsdlContentValidator validator = new WsdlContentValidator();
        Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.SYNTAX_ONLY, content, Collections.emptyMap());
        });
    }

    @Test
    public void testValidSemantics() throws Exception {
        TypedContent content = resourceToTypedContentHandle("wsdl-valid.wsdl");
        WsdlContentValidator validator = new WsdlContentValidator();
        validator.validate(ValidityLevel.FULL, content, Collections.emptyMap());
    }

    @Test
    public void testImportsNotFetched() throws Exception {
        TypedContent content = resourceToTypedContentHandle("wsdl-with-import.wsdl");
        WsdlContentValidator validator = new WsdlContentValidator();
        // Must not throw — if the import were fetched, the unreachable URL would cause a timeout/error
        validator.validate(ValidityLevel.FULL, content, Collections.emptyMap());
    }

    @Test
    public void testinValidSemantics() throws Exception {
        TypedContent content = resourceToTypedContentHandle("wsdl-invalid-semantics.wsdl");
        WsdlContentValidator validator = new WsdlContentValidator();
        Assertions.assertThrows(RuleViolationException.class, () -> {
            // WSDLException faultCode=INVALID_WSDL: Encountered illegal extension element
            // '{http://schemas.xmlsoap.org/wsdl/}element' in the context of a 'javax.wsdl.Types'. Extension
            // elements must be in a namespace other than WSDL's
            validator.validate(ValidityLevel.FULL, content, Collections.emptyMap());
        });
    }

    @Test
    public void testValidateReferencesAllMapped() {
        TypedContent content = resourceToTypedContentHandle("wsdl-with-refs.wsdl");
        List<ArtifactReference> refs = List.of(
                ArtifactReference.builder().name("base.wsdl").groupId("g").artifactId("c").version("1").build(),
                ArtifactReference.builder().name("order.xsd").groupId("g").artifactId("p").version("1").build());
        Assertions.assertDoesNotThrow(() -> new WsdlContentValidator().validateReferences(content, refs));
    }

    @Test
    public void testValidateReferencesUnmapped() {
        TypedContent content = resourceToTypedContentHandle("wsdl-with-refs.wsdl");
        List<ArtifactReference> refs = List.of(
                ArtifactReference.builder().name("base.wsdl").groupId("g").artifactId("c").version("1").build());
        RuleViolationException ex = Assertions.assertThrows(RuleViolationException.class,
                () -> new WsdlContentValidator().validateReferences(content, refs));
        Assertions.assertEquals(RuleType.INTEGRITY, ex.getRuleType());
        Assertions.assertEquals(1, ex.getCauses().size());
        RuleViolation v = ex.getCauses().iterator().next();
        Assertions.assertEquals("order.xsd", v.getContext());
        Assertions.assertEquals("Unmapped reference detected.", v.getDescription());
    }

    @Test
    public void testValidateReferencesMalformedIgnored() {
        TypedContent bad = TypedContent.create(ContentHandle.create("<wsdl:definitions"), ContentTypes.APPLICATION_XML);
        Assertions.assertDoesNotThrow(() -> new WsdlContentValidator().validateReferences(bad, List.of()));
    }
}
