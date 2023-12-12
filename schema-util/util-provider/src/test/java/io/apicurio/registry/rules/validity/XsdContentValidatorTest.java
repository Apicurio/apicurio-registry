package io.apicurio.registry.rules.validity;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rules.RuleViolationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;

public class XsdContentValidatorTest extends ArtifactUtilProviderTestBase {
    @Test
    public void testValidSyntax() throws Exception {
        ContentHandle contentA = resourceToContentHandle("xml-schema-valid.xsd");
        XsdContentValidator validator = new XsdContentValidator();
        validator.validate(ValidityLevel.SYNTAX_ONLY, contentA, Collections.emptyMap());
        ContentHandle contentB = resourceToContentHandle("xml-schema-invalid-semantics.xsd");
        validator.validate(ValidityLevel.SYNTAX_ONLY, contentB, Collections.emptyMap());
    }

    @Test
    public void testInvalidSyntax() throws Exception {
        ContentHandle content = resourceToContentHandle("xml-schema-invalid-syntax.xsd");
        XsdContentValidator validator = new XsdContentValidator();
        Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.SYNTAX_ONLY, content, Collections.emptyMap());
        });
    }

    @Test
    public void testValidSemantics() throws Exception {
        ContentHandle content = resourceToContentHandle("xml-schema-valid.xsd");
        XsdContentValidator validator = new XsdContentValidator();
        validator.validate(ValidityLevel.FULL, content, Collections.emptyMap());
    }

    @Test
    public void testInvalidSemantics() throws Exception {
        ContentHandle content = resourceToContentHandle("xml-schema-invalid-semantics.xsd");
        XsdContentValidator validator = new XsdContentValidator();
        Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.FULL, content, Collections.emptyMap());
        });
    }

}
