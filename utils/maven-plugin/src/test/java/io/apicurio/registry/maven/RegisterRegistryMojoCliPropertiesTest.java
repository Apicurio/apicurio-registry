package io.apicurio.registry.maven;

import io.apicurio.registry.rest.v3.beans.IfArtifactExists;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class RegisterRegistryMojoCliPropertiesTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void clearArtifactProperties() {
        System.getProperties().stringPropertyNames().stream()
                .filter(name -> name.startsWith("artifacts."))
                .collect(Collectors.toList())
                .forEach(System::clearProperty);
    }

    @Test
    void testLoadsSingleArtifactFromCliProperties() throws IOException, MojoExecutionException {
        Path artifactFile = Files.createFile(tempDir.resolve("schema.avsc"));

        System.setProperty("artifacts.groupId", "my.group.id");
        System.setProperty("artifacts.artifactId", "my-artifact-id");
        System.setProperty("artifacts.artifactType", "ASYNCAPI");
        System.setProperty("artifacts.file", artifactFile.toString());
        System.setProperty("artifacts.ifExists", "FIND_OR_CREATE_VERSION");
        System.setProperty("artifacts.canonicalize", "true");
        System.setProperty("artifacts.autoRefs", "true");

        RegisterRegistryMojo mojo = new RegisterRegistryMojo();
        mojo.validate();

        assertNotNull(mojo.artifacts);
        assertEquals(1, mojo.artifacts.size());

        RegisterArtifact artifact = mojo.artifacts.get(0);
        assertEquals("my.group.id", artifact.getGroupId());
        assertEquals("my-artifact-id", artifact.getArtifactId());
        assertEquals("ASYNCAPI", artifact.getArtifactType());
        assertEquals(artifactFile.toFile(), artifact.getFile());
        assertEquals(IfArtifactExists.FIND_OR_CREATE_VERSION, artifact.getIfExists());
        assertEquals(Boolean.TRUE, artifact.getCanonicalize());
        assertEquals(Boolean.TRUE, artifact.getAutoRefs());
    }

    @Test
    void testCliSupportedFieldsStayAlignedWithSimpleRegisterArtifactProperties() throws IntrospectionException {
        // If a new simple writable RegisterArtifact property is added, update the CLI mapping in
        // RegisterRegistryMojo.applyCliArtifactField and the expected set below.
        Set<String> expectedCliFields = Set.of(
                "groupId",
                "artifactId",
                "version",
                "artifactType",
                "file",
                "ifExists",
                "canonicalize",
                "minify",
                "autoRefs",
                "avroAutoRefsNamingStrategy",
                "isDraft",
                "contentType");

        Set<String> actualSimpleWritableProperties = Arrays.stream(
                        Introspector.getBeanInfo(RegisterArtifact.class, Object.class).getPropertyDescriptors())
                .filter(pd -> pd.getWriteMethod() != null)
                .filter(pd -> isCliSupportedScalarType(pd.getPropertyType()))
                .map(PropertyDescriptor::getName)
                .collect(Collectors.toSet());

        assertEquals(expectedCliFields, actualSimpleWritableProperties);
    }

    private static boolean isCliSupportedScalarType(Class<?> propertyType) {
        return propertyType == String.class
                || propertyType == File.class
                || propertyType == Boolean.class
                || propertyType == IfArtifactExists.class
                || propertyType == RegisterArtifact.AvroAutoRefsNamingStrategy.class;
    }
}
