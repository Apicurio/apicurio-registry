package io.apicurio.registry.noprofile.maven;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.maven.RegisterArtifact;
import io.apicurio.registry.maven.RegisterRegistryMojo;
import io.apicurio.registry.types.ArtifactType;
import org.apache.avro.Schema;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RegistryMojoTestBase extends AbstractResourceTestBase {
    protected File tempDirectory;

    protected final static String KEY_SUBJECT = "TestSubject-key";
    protected final static String VALUE_SUBJECT = "TestSubject-value";

    @BeforeEach
    public void createTempDirectory() throws IOException {
        this.tempDirectory = File.createTempFile(getClass().getSimpleName(), ".tmp");
        this.tempDirectory.delete();
        this.tempDirectory.mkdirs();
    }

    @AfterEach
    public void cleanupTempDirectory() {
        for (File tempFile : this.tempDirectory.listFiles()) {
            tempFile.delete();
        }
        this.tempDirectory.delete();
    }

    protected void writeContent(File outputPath, byte[] content) throws IOException {
        try (OutputStream writer = new FileOutputStream(outputPath)) {
            writer.write(content);
            writer.flush();
        }
    }

    protected void testRegister(RegisterRegistryMojo mojo, String groupId)
            throws IOException, MojoFailureException, MojoExecutionException {
        Schema keySchema = Schema.create(Schema.Type.STRING);
        Schema valueSchema = Schema.createUnion(
                Arrays.asList(Schema.create(Schema.Type.STRING), Schema.create(Schema.Type.NULL)));
        File keySchemaFile = new File(this.tempDirectory, KEY_SUBJECT + ".avsc");
        File valueSchemaFile = new File(this.tempDirectory, VALUE_SUBJECT + ".avsc");
        writeContent(keySchemaFile, keySchema.toString(true).getBytes(StandardCharsets.UTF_8));
        writeContent(valueSchemaFile, valueSchema.toString(true).getBytes(StandardCharsets.UTF_8));

        Assertions.assertTrue(keySchemaFile.isFile());
        Assertions.assertTrue(valueSchemaFile.isFile());

        List<RegisterArtifact> artifacts = createArtifacts(groupId, keySchemaFile, valueSchemaFile);

        mojo.setArtifacts(artifacts);
        mojo.execute();
    }

    private static List<RegisterArtifact> createArtifacts(String groupId, File keySchemaFile,
            File valueSchemaFile) {
        List<RegisterArtifact> artifacts = new ArrayList<>();

        RegisterArtifact keySchemaArtifact = new RegisterArtifact();
        keySchemaArtifact.setGroupId(groupId);
        keySchemaArtifact.setArtifactId(KEY_SUBJECT);
        keySchemaArtifact.setType(ArtifactType.AVRO);
        keySchemaArtifact.setFile(keySchemaFile);
        artifacts.add(keySchemaArtifact);

        RegisterArtifact valueSchemaArtifact = new RegisterArtifact();
        valueSchemaArtifact.setGroupId(groupId);
        valueSchemaArtifact.setArtifactId(VALUE_SUBJECT);
        valueSchemaArtifact.setType(ArtifactType.AVRO);
        valueSchemaArtifact.setFile(valueSchemaFile);
        artifacts.add(valueSchemaArtifact);
        return artifacts;
    }
}
