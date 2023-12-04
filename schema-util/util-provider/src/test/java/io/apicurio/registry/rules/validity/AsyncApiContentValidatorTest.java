package io.apicurio.registry.rules.validity;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rules.RuleViolationException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;

/**
 * Tests the AsyncAPI content validator.
 */
public class AsyncApiContentValidatorTest extends ArtifactUtilProviderTestBase {

    @Test
    public void testValidSyntax() throws Exception {
        ContentHandle content = resourceToContentHandle("asyncapi-valid-syntax.json");
        AsyncApiContentValidator validator = new AsyncApiContentValidator();
        validator.validate(ValidityLevel.SYNTAX_ONLY, content, Collections.emptyMap());
    }

    @Test
    public void testValidSyntax_AsyncApi25() throws Exception {
        ContentHandle content = resourceToContentHandle("asyncapi-valid-syntax-asyncapi25.json");
        AsyncApiContentValidator validator = new AsyncApiContentValidator();
        validator.validate(ValidityLevel.SYNTAX_ONLY, content, Collections.emptyMap());
    }

    @Test
    public void testValidSemantics() throws Exception {
        ContentHandle content = resourceToContentHandle("asyncapi-valid-semantics.json");
        AsyncApiContentValidator validator = new AsyncApiContentValidator();
        validator.validate(ValidityLevel.FULL, content, Collections.emptyMap());
    }

    @Test
    public void testInvalidSyntax() throws Exception {
        ContentHandle content = resourceToContentHandle("asyncapi-invalid-syntax.json");
        AsyncApiContentValidator validator = new AsyncApiContentValidator();
        Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.SYNTAX_ONLY, content, Collections.emptyMap());
        });
    }

    @Test
    public void testInvalidSemantics() throws Exception {
        ContentHandle content = resourceToContentHandle("asyncapi-invalid-semantics.json");
        AsyncApiContentValidator validator = new AsyncApiContentValidator();
        Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.FULL, content, Collections.emptyMap());
        });
    }

}
