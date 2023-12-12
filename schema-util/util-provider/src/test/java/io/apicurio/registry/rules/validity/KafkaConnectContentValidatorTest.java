package io.apicurio.registry.rules.validity;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rules.RuleViolationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;

/**
 * Tests the Kafka Connect content validator.
 */
public class KafkaConnectContentValidatorTest extends ArtifactUtilProviderTestBase {

    @Test
    public void testValidSyntax() throws Exception {
        ContentHandle content = resourceToContentHandle("kconnect-valid.json");
        KafkaConnectContentValidator validator = new KafkaConnectContentValidator();
        validator.validate(ValidityLevel.SYNTAX_ONLY, content, Collections.emptyMap());
    }

    @Test
    public void testInvalidSyntax() throws Exception {
        ContentHandle content = resourceToContentHandle("kconnect-invalid.json");
        KafkaConnectContentValidator validator = new KafkaConnectContentValidator();
        Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.SYNTAX_ONLY, content, Collections.emptyMap());
        });
    }

}
