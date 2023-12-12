package io.apicurio.registry.rules.validity;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.RuleViolationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tests the OpenAPI content validator.
 */
public class OpenApiContentValidatorTest extends ArtifactUtilProviderTestBase {

    @Test
    public void testValidSyntax() throws Exception {
        ContentHandle content = resourceToContentHandle("openapi-valid-syntax.json");
        OpenApiContentValidator validator = new OpenApiContentValidator();
        validator.validate(ValidityLevel.SYNTAX_ONLY, content, Collections.emptyMap());
    }

    @Test
    public void testValidSyntax_OpenApi31() throws Exception {
        ContentHandle content = resourceToContentHandle("openapi-valid-syntax-openapi31.json");
        OpenApiContentValidator validator = new OpenApiContentValidator();
        validator.validate(ValidityLevel.SYNTAX_ONLY, content, Collections.emptyMap());
    }

    @Test
    public void testValidSemantics() throws Exception {
        ContentHandle content = resourceToContentHandle("openapi-valid-semantics.json");
        OpenApiContentValidator validator = new OpenApiContentValidator();
        validator.validate(ValidityLevel.FULL, content, Collections.emptyMap());
    }

    @Test
    public void testInvalidSyntax() throws Exception {
        ContentHandle content = resourceToContentHandle("openapi-invalid-syntax.json");
        OpenApiContentValidator validator = new OpenApiContentValidator();
        Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.SYNTAX_ONLY, content, Collections.emptyMap());
        });
    }

    @Test
    public void testInvalidSemantics() throws Exception {
        ContentHandle content = resourceToContentHandle("openapi-invalid-semantics.json");
        OpenApiContentValidator validator = new OpenApiContentValidator();
        Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.FULL, content, Collections.emptyMap());
        });
    }

    @Test
    public void testValidateRefs() throws Exception {
        ContentHandle content = resourceToContentHandle("openapi-valid-with-refs.json");
        OpenApiContentValidator validator = new OpenApiContentValidator();
        validator.validate(ValidityLevel.SYNTAX_ONLY, content, Collections.emptyMap());

        // Properly map both required references - success.
        {
            List<ArtifactReference> references = new ArrayList<>();
            references.add(ArtifactReference.builder().groupId("default").artifactId("ExternalWidget")
                    .version("1.0").name("example.com#/components/schemas/ExternalWidget").build());
            references.add(ArtifactReference.builder().groupId("default").artifactId("AnotherWidget")
                    .version("1.1").name("example.com#/components/schemas/AnotherWidget").build());
            validator.validateReferences(content, references);
        }

        // Don't map either of the required references - failure.
        Assertions.assertThrows(RuleViolationException.class, () -> {
            List<ArtifactReference> references = new ArrayList<>();
            validator.validateReferences(content, references);
        });

        // Only map one of the two required refs - failure.
        Assertions.assertThrows(RuleViolationException.class, () -> {
            List<ArtifactReference> references = new ArrayList<>();
            references.add(ArtifactReference.builder().groupId("default").artifactId("AnotherWidget")
                    .version("1.1").name("example.com#/components/schemas/AnotherWidget").build());
            validator.validateReferences(content, references);
        });

        // Only map one of the two required refs - failure.
        Assertions.assertThrows(RuleViolationException.class, () -> {
            List<ArtifactReference> references = new ArrayList<>();
            references.add(ArtifactReference.builder().groupId("default").artifactId("AnotherWidget")
                    .version("1.1").name("example.com#/components/schemas/AnotherWidget").build());
            references.add(ArtifactReference.builder().groupId("default").artifactId("WrongWidget")
                    .version("2.3").name("example.com#/components/schemas/WrongWidget").build());
            validator.validateReferences(content, references);
        });
    }

}
