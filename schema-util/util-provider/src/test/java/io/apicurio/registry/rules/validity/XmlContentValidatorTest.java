package io.apicurio.registry.rules.validity;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rules.RuleViolationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;

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
}
