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

import io.apicurio.registry.client.RegistryService;
import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.tests.RegistryServiceTest;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.avro.Schema;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

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

    @RegistryServiceTest
    public void testDownloadIds(Supplier<RegistryService> supplier) throws Exception {
        String artifactId = generateArtifactId();

        Schema schema = Schema.createUnion(Arrays.asList(Schema.create(Schema.Type.STRING), Schema.create(Schema.Type.NULL)));
        CompletionStage<ArtifactMetaData> cs = supplier.get().createArtifact(ArtifactType.AVRO, artifactId, null, new ByteArrayInputStream(schema.toString().getBytes(StandardCharsets.UTF_8)));
        cs.toCompletableFuture().get();

        mojo.ids = Collections.singleton(artifactId);
        mojo.artifactExtension = ".avsc";
        mojo.outputDirectory = tempDirectory;
        mojo.execute();

        Assertions.assertTrue(new File(mojo.outputDirectory, artifactId + mojo.artifactExtension).exists());
    }

}
