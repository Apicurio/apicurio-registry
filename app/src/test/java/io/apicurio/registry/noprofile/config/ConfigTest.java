package io.apicurio.registry.noprofile.config;

import io.apicurio.registry.AbstractResourceTestBase;
import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.StreamSupport;

@QuarkusTest
public class ConfigTest extends AbstractResourceTestBase {

    @Test
    public void configurationTest() {
        Iterable<String> propertyNamesIterable = ConfigProvider.getConfig().getPropertyNames();

        List<String> offendingProperties = StreamSupport.stream(propertyNamesIterable.spliterator(), false)
                .filter(propertyName -> propertyName.startsWith("apicurio")
                        || propertyName.startsWith("registry"))
                .filter(this::propertyDontMatchPattern).peek(System.out::println).toList();

        Assertions.assertEquals(0, offendingProperties.size());
    }

    private boolean propertyDontMatchPattern(String propertyName) {
        return propertyName.chars().anyMatch(Character::isUpperCase);
    }
}
