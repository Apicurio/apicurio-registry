package io.apicurio.registry.rules.validity;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rules.RuleViolationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;

/**
 * Tests the GraphQL content validator.
 */
public class GraphQLContentValidatorTest extends ArtifactUtilProviderTestBase {

    @Test
    public void testValidSyntax() throws Exception {
        ContentHandle content = resourceToContentHandle("graphql-valid.graphql");
        GraphQLContentValidator validator = new GraphQLContentValidator();
        validator.validate(ValidityLevel.SYNTAX_ONLY, content, Collections.emptyMap());
    }

    @Test
    public void testInvalidSyntax() throws Exception {
        ContentHandle content = resourceToContentHandle("graphql-invalid.graphql");
        GraphQLContentValidator validator = new GraphQLContentValidator();
        Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.SYNTAX_ONLY, content, Collections.emptyMap());
        });
    }

}
