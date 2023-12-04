package io.apicurio.registry.rules.validity;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rules.RuleViolationException;

import java.util.Collections;


public class WsdlContentValidatorTest extends ArtifactUtilProviderTestBase {
    @Test
    public void testValidSyntax() throws Exception {
        ContentHandle contentA = resourceToContentHandle("wsdl-valid.wsdl");
        WsdlContentValidator validator = new WsdlContentValidator();
        validator.validate(ValidityLevel.SYNTAX_ONLY, contentA, Collections.emptyMap());
        ContentHandle contentB = resourceToContentHandle("wsdl-invalid-semantics.wsdl");
        validator.validate(ValidityLevel.SYNTAX_ONLY, contentB, Collections.emptyMap());
    }

    @Test
    public void testinValidSyntax() throws Exception {
        ContentHandle content = resourceToContentHandle("wsdl-invalid-syntax.wsdl");
        WsdlContentValidator validator = new WsdlContentValidator();
        Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.SYNTAX_ONLY, content, Collections.emptyMap());
        });
    }

    @Test
    public void testValidSemantics() throws Exception {
        ContentHandle content = resourceToContentHandle("wsdl-valid.wsdl");
        WsdlContentValidator validator = new WsdlContentValidator();
        validator.validate(ValidityLevel.FULL, content, Collections.emptyMap());
    }

    @Test
    public void testinValidSemantics() throws Exception {
        ContentHandle content = resourceToContentHandle("wsdl-invalid-semantics.wsdl");
        WsdlContentValidator validator = new WsdlContentValidator();
        Assertions.assertThrows(RuleViolationException.class, () -> {
            //WSDLException faultCode=INVALID_WSDL: Encountered illegal extension element '{http://schemas.xmlsoap.org/wsdl/}element' in the context of a 'javax.wsdl.Types'. Extension elements must be in a namespace other than WSDL's
            validator.validate(ValidityLevel.FULL, content, Collections.emptyMap());
        });
    }
}
