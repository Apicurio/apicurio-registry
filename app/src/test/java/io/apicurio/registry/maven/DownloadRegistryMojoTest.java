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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

import org.apache.avro.Schema;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.types.ArtifactType;
import io.quarkus.test.junit.QuarkusTest;

/**
 * @author Ales Justin
 */
@QuarkusTest
public class DownloadRegistryMojoTest extends RegistryMojoTestBase {
    DownloadRegistryMojo mojo;

    @BeforeEach
    public void createMojo() {
        this.mojo = new DownloadRegistryMojo();
        this.mojo.registryUrl = "http://localhost:8081/api";
    }

    @Test
    public void testDownloadIds() throws Exception {
        String artifactId = generateArtifactId();

        Schema schema = Schema.createUnion(Arrays.asList(Schema.create(Schema.Type.STRING), Schema.create(Schema.Type.NULL)));
        client.createArtifact(artifactId, ArtifactType.AVRO, new ByteArrayInputStream(schema.toString().getBytes(StandardCharsets.UTF_8)));
        
        this.waitForArtifact(artifactId);

        mojo.ids = Collections.singleton(artifactId);
        mojo.artifactExtension = ".avsc";
        mojo.outputDirectory = tempDirectory;
        mojo.execute();

        Assertions.assertTrue(new File(mojo.outputDirectory, artifactId + mojo.artifactExtension).exists());
    }

}
