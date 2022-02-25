/*
 * Copyright 2022 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.noprofile.maven;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.maven.RegisterArtifact;
import io.apicurio.registry.maven.RegisterRegistryMojo;
import io.apicurio.registry.types.ArtifactType;
import org.apache.avro.Schema;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * @author Ales Justin
 */
public class RegistryMojoTestBase extends AbstractResourceTestBase {
    protected File tempDirectory;

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

    protected void testRegister(RegisterRegistryMojo mojo, String groupId) throws IOException, MojoFailureException, MojoExecutionException {
        String keySubject = "TestSubject-key";
        String valueSubject = "TestSubject-value";
        Schema keySchema = Schema.create(Schema.Type.STRING);
        Schema valueSchema = Schema.createUnion(Arrays.asList(Schema.create(Schema.Type.STRING), Schema.create(Schema.Type.NULL)));
        File keySchemaFile = new File(this.tempDirectory, keySubject + ".avsc");
        File valueSchemaFile = new File(this.tempDirectory, valueSubject + ".avsc");
        writeContent(keySchemaFile, keySchema.toString(true).getBytes(StandardCharsets.UTF_8));
        writeContent(valueSchemaFile, valueSchema.toString(true).getBytes(StandardCharsets.UTF_8));

        assertTrue(keySchemaFile.isFile());
        assertTrue(valueSchemaFile.isFile());

        List<RegisterArtifact> artifacts = new ArrayList<>();

        RegisterArtifact keySchemaArtifact = new RegisterArtifact();
        keySchemaArtifact.setGroupId(groupId);
        keySchemaArtifact.setArtifactId(keySubject);
        keySchemaArtifact.setType(ArtifactType.AVRO);
        keySchemaArtifact.setFile(keySchemaFile);
        artifacts.add(keySchemaArtifact);

        RegisterArtifact valueSchemaArtifact = new RegisterArtifact();
        valueSchemaArtifact.setGroupId(groupId);
        valueSchemaArtifact.setArtifactId(valueSubject);
        valueSchemaArtifact.setType(ArtifactType.AVRO);
        valueSchemaArtifact.setFile(valueSchemaFile);
        artifacts.add(valueSchemaArtifact);

        mojo.setArtifacts(artifacts);
        mojo.execute();
    }
}
