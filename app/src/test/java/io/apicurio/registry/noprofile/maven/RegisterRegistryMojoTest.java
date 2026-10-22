package io.apicurio.registry.noprofile.maven;

import io.apicurio.registry.maven.RegisterArtifact;
import io.apicurio.registry.maven.RegisterRegistryMojo;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@QuarkusTest
public class RegisterRegistryMojoTest extends RegistryMojoTestBase {
    RegisterRegistryMojo mojo;

    private static final String groupId = "RegisterRegistryMojoTest";

    @BeforeEach
    public void createMojo() {
        this.mojo = new RegisterRegistryMojo();
        this.mojo.setRegistryUrl(TestUtils.getRegistryV3ApiUrl(testPort));
    }

    @Test
    public void testRegister() throws IOException, MojoFailureException, MojoExecutionException {
        super.testRegister(mojo, groupId);

        Assertions.assertNotNull(
                clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(KEY_SUBJECT).get());
        Assertions.assertNotNull(
                clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(VALUE_SUBJECT).get());
    }

    @Test
    public void testRegisterFromFileClosesFileInputStream()
            throws IOException, MojoFailureException, MojoExecutionException {
        Path procSelfFd = Path.of("/proc/self/fd");
        Assumptions.assumeTrue(Files.isDirectory(procSelfFd), "/proc/self/fd is not available on this OS");

        File schemaFile = new File(this.tempDirectory, "fd-leak-schema.avsc");
        Files.write(schemaFile.toPath(), "\"string\"".getBytes(StandardCharsets.UTF_8));

        RegisterArtifact artifact = new RegisterArtifact();
        artifact.setGroupId(groupId);
        artifact.setArtifactId("fd-leak-artifact");
        artifact.setArtifactType(ArtifactType.AVRO);
        artifact.setFile(schemaFile);
        mojo.setArtifacts(List.of(artifact));

        Assertions.assertEquals(0, countOpenFileDescriptorsFor(procSelfFd, schemaFile),
                "sanity check: no fd should reference the schema file before registration");

        mojo.execute();

        Assertions.assertEquals(0, countOpenFileDescriptorsFor(procSelfFd, schemaFile),
                "registering an artifact from a file must close the underlying FileInputStream");
    }

    private static long countOpenFileDescriptorsFor(Path procSelfFd, File file) throws IOException {
        Path canonicalTarget = file.getCanonicalFile().toPath();
        try (var fdEntries = Files.list(procSelfFd)) {
            return fdEntries.filter(fd -> {
                try {
                    return canonicalTarget.equals(Files.readSymbolicLink(fd).toAbsolutePath().normalize());
                } catch (IOException e) {
                    // fd was closed concurrently, or does not resolve to a regular file; not a match.
                    return false;
                }
            }).count();
        }
    }

    @Test
    public void testSkipRegister() throws IOException, MojoFailureException, MojoExecutionException {
        this.mojo.setSkip(true);
        super.testRegister(mojo, groupId);

        Assertions.assertThrows(Exception.class,
                () -> clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(KEY_SUBJECT).get());
        Assertions.assertThrows(Exception.class,
                () -> clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(VALUE_SUBJECT).get());
    }
}
