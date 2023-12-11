package io.apicurio.registry.rules.validity;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.RuleViolationException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tests the Protobuf content validator.
 */
public class ProtobufContentValidatorTest extends ArtifactUtilProviderTestBase {

    @Test
    public void testValidProtobufSchema() throws Exception {
        ContentHandle content = resourceToContentHandle("protobuf-valid.proto");
        ProtobufContentValidator validator = new ProtobufContentValidator();
        validator.validate(ValidityLevel.SYNTAX_ONLY, content, Collections.emptyMap());
    }

    @Test
    public void testInvalidProtobufSchema() throws Exception {
        ContentHandle content = resourceToContentHandle("protobuf-invalid.proto");
        ProtobufContentValidator validator = new ProtobufContentValidator();
        Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.SYNTAX_ONLY, content, Collections.emptyMap());
        });
    }

    @Test
    public void testValidateProtobufWithImports() throws Exception {
        ContentHandle mode = resourceToContentHandle("mode.proto");
        ContentHandle tableInfo = resourceToContentHandle("table_info.proto");
        ProtobufContentValidator validator = new ProtobufContentValidator();
        validator.validate(ValidityLevel.SYNTAX_ONLY, tableInfo, Collections.singletonMap("mode.proto", mode));
    }

    @Test
    public void testValidateReferences() throws Exception {
        ContentHandle content = resourceToContentHandle("protobuf-valid-with-refs.proto");
        ProtobufContentValidator validator = new ProtobufContentValidator();

        // Properly map both required references - success.
        {
            List<ArtifactReference> references = new ArrayList<>();
            references.add(ArtifactReference.builder()
                    .groupId("default")
                    .artifactId("message2.proto")
                    .version("1.0")
                    .name("message2.proto").build());
            references.add(ArtifactReference.builder()
                    .groupId("default")
                    .artifactId("message3.proto")
                    .version("1.1")
                    .name("message3.proto").build());
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
            references.add(ArtifactReference.builder()
                    .groupId("default")
                    .artifactId("message2.proto")
                    .version("1.0")
                    .name("message2.proto").build());
            validator.validateReferences(content, references);
        });

        // Only map one of the two required refs - failure.
        Assertions.assertThrows(RuleViolationException.class, () -> {
            List<ArtifactReference> references = new ArrayList<>();
            references.add(ArtifactReference.builder()
                    .groupId("default")
                    .artifactId("message2.proto")
                    .version("1.0")
                    .name("message2.proto").build());
            references.add(ArtifactReference.builder()
                    .groupId("default")
                    .artifactId("message4.proto")
                    .version("4.0")
                    .name("message4.proto").build());
            validator.validateReferences(content, references);
        });
    }

}
