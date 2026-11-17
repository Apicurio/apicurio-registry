package io.apicurio.registry.rules.validity;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.xml.rules.validity.XmlContentValidator;
import io.apicurio.registry.xsd.rules.validity.XsdContentValidator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

public class XmlContentValidatorTest extends ArtifactUtilProviderTestBase {
    @Test
    public void testValidSyntax() throws Exception {
        TypedContent content = resourceToTypedContentHandle("xml-valid.xml");
        XsdContentValidator validator = new XsdContentValidator();
        validator.validate(ValidityLevel.SYNTAX_ONLY, content, Collections.emptyMap());
    }

    @Test
    public void testInvalidSyntax() throws Exception {
        TypedContent content = resourceToTypedContentHandle("xml-invalid-syntax.xml");
        XsdContentValidator validator = new XsdContentValidator();
        Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.SYNTAX_ONLY, content, Collections.emptyMap());
        });
    }

    @Test
    public void testValidateReferencesNoOpForPlainXml() {
        String xml = "<root xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">"
                + "<xs:import namespace=\"urn:x\" schemaLocation=\"other.xsd\"/></root>";
        TypedContent content = TypedContent.create(ContentHandle.create(xml), ContentTypes.APPLICATION_XML);
        Assertions.assertDoesNotThrow(() -> new XmlContentValidator().validateReferences(content, List.of()));
    }
}
