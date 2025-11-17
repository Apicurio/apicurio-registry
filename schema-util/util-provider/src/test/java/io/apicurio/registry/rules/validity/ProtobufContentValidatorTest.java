package io.apicurio.registry.rules.validity;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.RuleViolationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tests the Protobuf content validator.
 */
public class ProtobufContentValidatorTest extends ArtifactUtilProviderTestBase {

    @Test
    public void testValidProtobufSchema() throws Exception {
        TypedContent content = resourceToTypedContentHandle("protobuf-valid.proto");
        ProtobufContentValidator validator = new ProtobufContentValidator();
        validator.validate(ValidityLevel.SYNTAX_ONLY, content, Collections.emptyMap());
    }

    @Test
    public void testInvalidProtobufSchema() throws Exception {
        TypedContent content = resourceToTypedContentHandle("protobuf-invalid.proto");
        ProtobufContentValidator validator = new ProtobufContentValidator();
        Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.SYNTAX_ONLY, content, Collections.emptyMap());
        });
    }

    @Test
    public void testValidateProtobufWithImports() throws Exception {
        TypedContent mode = resourceToTypedContentHandle("mode.proto");
        TypedContent tableInfo = resourceToTypedContentHandle("table_info.proto");
        ProtobufContentValidator validator = new ProtobufContentValidator();
        validator.validate(ValidityLevel.SYNTAX_ONLY, tableInfo,
                Collections.singletonMap("sample/mode.proto", mode));
    }

    @Test
    public void testValidateReferences() throws Exception {
        TypedContent content = resourceToTypedContentHandle("protobuf-valid-with-refs.proto");
        ProtobufContentValidator validator = new ProtobufContentValidator();

        // Properly map both required references - success.
        {
            List<ArtifactReference> references = new ArrayList<>();
            references.add(ArtifactReference.builder().groupId("default").artifactId("message2.proto")
                    .version("1.0").name("message2.proto").build());
            references.add(ArtifactReference.builder().groupId("default").artifactId("message3.proto")
                    .version("1.1").name("message3.proto").build());
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
            references.add(ArtifactReference.builder().groupId("default").artifactId("message2.proto")
                    .version("1.0").name("message2.proto").build());
            validator.validateReferences(content, references);
        });

        // Only map one of the two required refs - failure.
        Assertions.assertThrows(RuleViolationException.class, () -> {
            List<ArtifactReference> references = new ArrayList<>();
            references.add(ArtifactReference.builder().groupId("default").artifactId("message2.proto")
                    .version("1.0").name("message2.proto").build());
            references.add(ArtifactReference.builder().groupId("default").artifactId("message4.proto")
                    .version("4.0").name("message4.proto").build());
            validator.validateReferences(content, references);
        });
    }

    /**
     * Test that schemas with duplicate field tag numbers are accepted with SYNTAX_ONLY
     * but rejected with FULL validation.
     * Case 1 from GitHub issue #6209.
     */
    @Test
    public void testProtobufWithDuplicateTagNumbers() throws Exception {
        TypedContent content = resourceToTypedContentHandle("protobuf-duplicate-tags.proto");
        ProtobufContentValidator validator = new ProtobufContentValidator();

        // SYNTAX_ONLY should pass (Wire parser doesn't catch duplicate tags)
        validator.validate(ValidityLevel.SYNTAX_ONLY, content, Collections.emptyMap());

        // FULL validation should fail (Google Protobuf catches duplicate tags)
        Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.FULL, content, Collections.emptyMap());
        });
    }

    /**
     * Test that schemas with negative field tag numbers are accepted with SYNTAX_ONLY
     * but rejected with FULL validation.
     * Case 2 from GitHub issue #6209.
     */
    @Test
    public void testProtobufWithNegativeTagNumber() throws Exception {
        TypedContent content = resourceToTypedContentHandle("protobuf-negative-tag.proto");
        ProtobufContentValidator validator = new ProtobufContentValidator();

        // SYNTAX_ONLY should pass (Wire parser doesn't catch negative tags)
        validator.validate(ValidityLevel.SYNTAX_ONLY, content, Collections.emptyMap());

        // FULL validation should fail (Google Protobuf catches negative tags)
        Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.FULL, content, Collections.emptyMap());
        });
    }

    /**
     * Test that schemas with invalid field types are accepted with SYNTAX_ONLY
     * but rejected with FULL validation.
     * Case 3 from GitHub issue #6209.
     */
    @Test
    public void testProtobufWithInvalidType() throws Exception {
        TypedContent content = resourceToTypedContentHandle("protobuf-invalid-type.proto");
        ProtobufContentValidator validator = new ProtobufContentValidator();

        // SYNTAX_ONLY should pass (Wire parser doesn't catch undefined types at parse time)
        validator.validate(ValidityLevel.SYNTAX_ONLY, content, Collections.emptyMap());

        // FULL validation should fail (Google Protobuf catches undefined types)
        Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.FULL, content, Collections.emptyMap());
        });
    }

    /**
     * Test that schemas with invalid option values are accepted with SYNTAX_ONLY
     * but rejected with FULL validation.
     * Case 4 from GitHub issue #6209.
     */
    @Test
    public void testProtobufWithInvalidOptionValue() throws Exception {
        TypedContent content = resourceToTypedContentHandle("protobuf-invalid-option.proto");
        ProtobufContentValidator validator = new ProtobufContentValidator();

        // SYNTAX_ONLY should pass (Wire parser doesn't validate option types)
        validator.validate(ValidityLevel.SYNTAX_ONLY, content, Collections.emptyMap());

        // FULL validation should fail (Google Protobuf catches invalid option types)
        Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.FULL, content, Collections.emptyMap());
        });
    }

}
