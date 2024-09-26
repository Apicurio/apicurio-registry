package io.apicurio.registry.noprofile.config;

import io.apicurio.registry.AbstractResourceTestBase;
import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.stream.StreamSupport;

@QuarkusTest
public class ConfigTest extends AbstractResourceTestBase {

    @Test
    public void configurationTest() {
        Iterable<String> propertyNamesIterable = ConfigProvider.getConfig().getPropertyNames();

        Assertions.assertTrue(StreamSupport.stream(propertyNamesIterable.spliterator(), false)
                .filter(propertyName -> propertyName.startsWith("apicurio") || propertyName.startsWith("registry"))
                .allMatch(this::ensurePropertyMatchPattern));
    }

    private boolean ensurePropertyMatchPattern(String propertyName) {
        boolean patternMatched = propertyName.chars().noneMatch(Character::isUpperCase);
        if (!patternMatched) {
            Assertions.fail(String.format("""
                    Property %s does not match required pattern:\s
                     No environment variable redefinition, there's only one way for configuring a value.
                     All Apicurio related env vars are runtime by default.
                     Quarkus values are use whenever possible.
                     All lower case letters using snake-case formatting.""", propertyName));
        } return true;
    }
}
