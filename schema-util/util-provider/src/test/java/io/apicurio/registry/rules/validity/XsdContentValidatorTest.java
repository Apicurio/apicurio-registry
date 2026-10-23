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
}
