package io.apicurio.registry.maven;

import io.apicurio.registry.rest.v3.beans.IfArtifactExists;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
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
    void testLoadsNestedArtifactListsFromCliProperties() throws IOException, MojoExecutionException {
        Path artifactFile = Files.createFile(tempDir.resolve("api.yaml"));
        Path protoPathOne = Files.createDirectory(tempDir.resolve("proto"));
        Path protoPathTwo = Files.createDirectory(tempDir.resolve("shared-proto"));

        System.setProperty("artifacts.groupId", "my.group.id");
        System.setProperty("artifacts.artifactId", "my-artifact-id");
        System.setProperty("artifacts.artifactType", "ASYNCAPI");
        System.setProperty("artifacts.file", artifactFile.toString());

        System.setProperty("artifacts.references.0.name", "ref-name");
        System.setProperty("artifacts.references.0.groupId", "ref.group");
        System.setProperty("artifacts.references.0.artifactId", "ref-artifact");
        System.setProperty("artifacts.references.0.version", "1.0.0");

        System.setProperty("artifacts.existingReferences.0.resourceName", "./schemas/shared.avsc");
        System.setProperty("artifacts.existingReferences.0.groupId", "existing.group");
        System.setProperty("artifacts.existingReferences.0.artifactId", "existing-artifact");
        System.setProperty("artifacts.existingReferences.0.version", "2.0.0");

        System.setProperty("artifacts.protoPaths.0", protoPathOne.toString());
        System.setProperty("artifacts.protoPaths.1", protoPathTwo.toString());

        RegisterRegistryMojo mojo = new RegisterRegistryMojo();
        mojo.validate();

        RegisterArtifact artifact = mojo.artifacts.get(0);

        assertNotNull(artifact.getReferences());
        assertEquals(1, artifact.getReferences().size());
        RegisterArtifactReference reference = artifact.getReferences().get(0);
        assertEquals("ref-name", reference.getName());
        assertEquals("ref.group", reference.getGroupId());
        assertEquals("ref-artifact", reference.getArtifactId());
        assertEquals("1.0.0", reference.getVersion());

        assertNotNull(artifact.getExistingReferences());
        assertEquals(1, artifact.getExistingReferences().size());
        ExistingReference existingReference = artifact.getExistingReferences().get(0);
        assertEquals("./schemas/shared.avsc", existingReference.getResourceName());
        assertEquals("existing.group", existingReference.getGroupId());
        assertEquals("existing-artifact", existingReference.getArtifactId());
        assertEquals("2.0.0", existingReference.getVersion());

        assertEquals(List.of(protoPathOne.toFile(), protoPathTwo.toFile()), artifact.getProtoPaths());
    }

    @Test
    void testCliSupportedFieldsStayAlignedWithRegisterArtifactProperties() throws IntrospectionException {
        // If a new writable RegisterArtifact property is added, update the CLI system-property
        // support and the expected set below.
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
                "contentType",
                "references",
                "existingReferences",
                "protoPaths");

        Set<String> actualWritableProperties = Arrays.stream(
                        Introspector.getBeanInfo(RegisterArtifact.class, Object.class).getPropertyDescriptors())
                .filter(pd -> pd.getWriteMethod() != null)
                .map(PropertyDescriptor::getName)
                .collect(Collectors.toSet());

        assertEquals(expectedCliFields, actualWritableProperties);
    }
}
