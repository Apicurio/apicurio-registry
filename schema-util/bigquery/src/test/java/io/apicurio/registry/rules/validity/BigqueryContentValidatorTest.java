package io.apicurio.registry.rules.validity;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rules.RuleViolationException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.fail;

class BigqueryContentValidatorTest {

    private BigqueryContentValidator validator = new BigqueryContentValidator();

    @Test
    public void testValid() throws IOException {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("validschema.json")) {
            validator.validate(ValidityLevel.FULL, ContentHandle.create(inputStream), Collections.emptyMap());
        }
    }

    @Test
    public void testInvalid() throws IOException {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("invalidschema.json")) {
            validator.validate(ValidityLevel.FULL, ContentHandle.create(inputStream), Collections.emptyMap());
            fail("RuleViolationException expected");
        } catch (RuleViolationException e) {

        }
    }

    @Test
    public void testNotASchema() throws IOException {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("notaschema.json")) {
            validator.validate(ValidityLevel.FULL, ContentHandle.create(inputStream), Collections.emptyMap());
            fail("RuleViolationException expected");
        } catch (RuleViolationException e) {

        }
    }
}