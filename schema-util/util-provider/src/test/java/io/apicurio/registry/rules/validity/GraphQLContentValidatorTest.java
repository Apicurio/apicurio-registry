package io.apicurio.registry.rules.validity;

import io.apicurio.registry.content.TypedContent;
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
        TypedContent content = resourceToTypedContentHandle("graphql-valid.graphql");
        GraphQLContentValidator validator = new GraphQLContentValidator();
        validator.validate(ValidityLevel.SYNTAX_ONLY, content, Collections.emptyMap());
    }

    @Test
    public void testInvalidSyntax() throws Exception {
        TypedContent content = resourceToTypedContentHandle("graphql-invalid.graphql");
        GraphQLContentValidator validator = new GraphQLContentValidator();
        Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.SYNTAX_ONLY, content, Collections.emptyMap());
        });
    }

}
