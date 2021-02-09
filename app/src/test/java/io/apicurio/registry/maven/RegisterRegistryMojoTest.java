/*
 * Copyright 2018 Confluent Inc. (adapted from their MojoTest)
 * Copyright 2020 Red Hat
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

package io.apicurio.registry.maven;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.avro.Schema;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;

/**
 * @author Ales Justin
 */
@QuarkusTest
public class RegisterRegistryMojoTest extends RegistryMojoTestBase {
    RegisterRegistryMojo mojo;

    @BeforeEach
    public void createMojo() {
        this.mojo = new RegisterRegistryMojo();
        this.mojo.registryUrl = TestUtils.getRegistryV2ApiUrl();
    }

    @Test
    public void testRegister() throws IOException, MojoFailureException, MojoExecutionException {
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
        keySchemaArtifact.setGroupId("RegisterRegistryMojoTest");
        keySchemaArtifact.setArtifactId(keySubject);
        keySchemaArtifact.setType(ArtifactType.AVRO);
        keySchemaArtifact.setFile(keySchemaFile);
        artifacts.add(keySchemaArtifact);

        RegisterArtifact valueSchemaArtifact = new RegisterArtifact();
        valueSchemaArtifact.setGroupId("RegisterRegistryMojoTest");
        valueSchemaArtifact.setArtifactId(valueSubject);
        valueSchemaArtifact.setType(ArtifactType.AVRO);
        valueSchemaArtifact.setFile(valueSchemaFile);
        artifacts.add(valueSchemaArtifact);

        mojo.artifacts = artifacts;
        mojo.execute();
    }
}
