/*
 * Copyright 2018 Confluent Inc. (adapted from their MojoTest)
 * Copyright 2019 Red Hat
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

import io.apicurio.registry.types.ArtifactType;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.avro.Schema;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Ales Justin
 */
@QuarkusTest
public class RegisterRegistryMojoTest extends RegistryMojoTestBase {
    RegisterRegistryMojo mojo;

    @BeforeEach
    public void createMojo() {
        this.mojo = new RegisterRegistryMojo();
        this.mojo.registryUrl = "http://localhost:8081/api";
    }

    @Test
    public void testRegister() throws IOException, MojoFailureException, MojoExecutionException {
        Map<String, Integer> expectedVersions = new LinkedHashMap<>();

        Map<String, File> idToFile = new LinkedHashMap<>();
        int version = 1;
        for (int i = 0; i < 10; i++) {
            String keySubject = String.format("TestSubject%03d-key", i);
            String valueSubject = String.format("TestSubject%03d-value", i);
            Schema keySchema = Schema.create(Schema.Type.STRING);
            Schema valueSchema = Schema.createUnion(Arrays.asList(Schema.create(Schema.Type.STRING), Schema.create(Schema.Type.NULL)));
            File keySchemaFile = new File(this.tempDirectory, keySubject + ".avsc");
            File valueSchemaFile = new File(this.tempDirectory, valueSubject + ".avsc");
            writeContent(keySchemaFile, keySchema.toString(true).getBytes(StandardCharsets.UTF_8));
            writeContent(valueSchemaFile, valueSchema.toString(true).getBytes(StandardCharsets.UTF_8));
            idToFile.put(keySubject, keySchemaFile);
            expectedVersions.put(keySubject, version);
            idToFile.put(valueSubject, valueSchemaFile);
            expectedVersions.put(valueSubject, version);
        }

        mojo.artifacts = idToFile;
        mojo.artifactType = ArtifactType.AVRO;
        mojo.execute();

        Assertions.assertEquals(mojo.artifactVersions, expectedVersions);
    }
}
