package io.apicurio.registry.maven;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RegisterRegistryMojoApiInfoVersionTest {

    @TempDir
    Path tempDir;

    @Test
    void testResolveVersionExtractsYamlInfoVersionAndMarksSnapshotAsDraft() throws Exception {
        Path artifactFile = tempDir.resolve("asyncapi.yml");
        Files.writeString(artifactFile, """
                asyncapi: 3.0.0
                info:
                  title: Inventory API
                  version: 1.2.3-SNAPSHOT
                channels: {}
                """);

        RegisterArtifact artifact = new RegisterArtifact();
        artifact.setArtifactType("ASYNCAPI");
        artifact.setFile(artifactFile.toFile());
        artifact.setVersionStrategy(RegisterArtifact.VersionStrategy.API_INFO_VERSION);

        RegisterRegistryMojo mojo = new RegisterRegistryMojo();
        Object resolved = invokeResolveVersion(mojo, artifact, Files.readString(artifactFile));

        assertEquals("1.2.3", invokeResolvedAccessor(resolved, "version"));
        assertEquals(Boolean.TRUE, invokeResolvedAccessor(resolved, "isDraft"));
        assertEquals(Boolean.TRUE, invokeResolvedAccessor(resolved, "derivedFromApiInfoVersion"));
        assertEquals(Boolean.TRUE, invokeResolvedAccessor(resolved, "derivedFromSnapshot"));
        assertEquals(Boolean.TRUE, invokeResolvedAccessor(resolved, "shouldUpdateDraftContentOnConflict"));
        assertEquals(Boolean.FALSE, invokeResolvedAccessor(resolved, "shouldPromoteDraftOnConflict"));
    }

    @Test
    void testResolveVersionExtractsYamlInfoVersionWithoutChangingDraft() throws Exception {
        Path artifactFile = tempDir.resolve("asyncapi.yml");
        Files.writeString(artifactFile, """
                asyncapi: 3.0.0
                info:
                  title: Inventory API
                  version: 1.2.3
                channels: {}
                """);

        RegisterArtifact artifact = new RegisterArtifact();
        artifact.setArtifactType("ASYNCAPI");
        artifact.setFile(artifactFile.toFile());
        artifact.setVersionStrategy(RegisterArtifact.VersionStrategy.API_INFO_VERSION);
        artifact.setIsDraft(Boolean.FALSE);

        RegisterRegistryMojo mojo = new RegisterRegistryMojo();
        Object resolved = invokeResolveVersion(mojo, artifact, Files.readString(artifactFile));

        assertEquals("1.2.3", invokeResolvedAccessor(resolved, "version"));
        assertEquals(Boolean.FALSE, invokeResolvedAccessor(resolved, "isDraft"));
        assertEquals(Boolean.TRUE, invokeResolvedAccessor(resolved, "derivedFromApiInfoVersion"));
        assertEquals(Boolean.FALSE, invokeResolvedAccessor(resolved, "derivedFromSnapshot"));
        assertEquals(Boolean.FALSE, invokeResolvedAccessor(resolved, "shouldUpdateDraftContentOnConflict"));
        assertEquals(Boolean.TRUE, invokeResolvedAccessor(resolved, "shouldPromoteDraftOnConflict"));
    }

    private static Object invokeResolveVersion(RegisterRegistryMojo mojo, RegisterArtifact artifact, String data)
            throws Exception {
        Method method = RegisterRegistryMojo.class.getDeclaredMethod("resolveVersion",
                RegisterArtifact.class, String.class);
        method.setAccessible(true);
        return method.invoke(mojo, artifact, data);
    }

    private static Object invokeResolvedAccessor(Object target, String methodName) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        return method.invoke(target);
    }
}
