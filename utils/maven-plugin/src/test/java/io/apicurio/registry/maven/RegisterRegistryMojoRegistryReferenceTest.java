package io.apicurio.registry.maven;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RegisterRegistryMojoRegistryReferenceTest {

    @Test
    void testParseRegistryReferenceLocationAcceptsSameRegistryAndDecodesSegments() throws Exception {
        RegisterRegistryMojo mojo = new RegisterRegistryMojo();
        mojo.setRegistryUrl("http://localhost/apis/registry/v3/");

        Optional<?> location = parseLocation(mojo,
                "http://localhost:80/apis/registry/v3/groups/master%20group/artifacts/kafka-bindings.yml/"
                        + "versions/branch%3Dlatest/content");

        assertTrue(location.isPresent());
        assertEquals("master group", readField(location.get(), "groupId"));
        assertEquals("kafka-bindings.yml", readField(location.get(), "artifactId"));
        assertEquals("branch=latest", readField(location.get(), "versionExpression"));
    }

    @Test
    void testParseRegistryReferenceLocationRejectsDifferentRegistry() throws Exception {
        RegisterRegistryMojo mojo = new RegisterRegistryMojo();
        mojo.setRegistryUrl("http://localhost/apis/registry/v3/");

        Optional<?> location = parseLocation(mojo,
                "http://otherhost/apis/registry/v3/groups/master/artifacts/kafka-bindings.yml/versions/1");

        assertFalse(location.isPresent());
    }

    private static Optional<?> parseLocation(RegisterRegistryMojo mojo, String resource) throws Exception {
        Method method = RegisterRegistryMojo.class
                .getDeclaredMethod("parseRegistryReferenceLocation", String.class);
        method.setAccessible(true);
        return (Optional<?>) method.invoke(mojo, resource);
    }

    private static Object readField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
