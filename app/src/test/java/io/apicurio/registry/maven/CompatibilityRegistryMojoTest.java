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

import io.apicurio.registry.client.RegistryClient;
import io.apicurio.registry.client.RegistryService;
import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.rest.beans.Rule;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.avro.Schema;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * @author Ales Justin
 */
@QuarkusTest
public class CompatibilityRegistryMojoTest extends RegistryMojoTestBase {
    CompatibilityRegistryMojo mojo;

    @BeforeEach
    public void createMojo() {
        this.mojo = new CompatibilityRegistryMojo();
        this.mojo.registryUrl = "http://localhost:8081";
    }

    @Test
    public void testCompatibility() throws Exception {
        String artifactId = UUID.randomUUID().toString();

        try (RegistryService client = RegistryClient.create(mojo.registryUrl)) {
            Schema schema = new Schema.Parser().parse("{\"namespace\": \"example.avro\"," +
                                                       " \"type\": \"record\"," +
                                                       " \"name\": \"user\"," +
                                                       " \"fields\": [" +
                                                       "     {\"name\": \"name\", \"type\": \"string\"}," +
                                                       "     {\"name\": \"favorite_number\",  \"type\": \"int\"}" +
                                                       " ]" +
                                                       "}");
            CompletionStage<ArtifactMetaData> cs = client.createArtifact(ArtifactType.AVRO, artifactId, new ByteArrayInputStream(schema.toString().getBytes()));
            cs.toCompletableFuture().get();

            Rule rule = new Rule();
            rule.setType(RuleType.COMPATIBILITY);
            rule.setConfig("BACKWARD");
            client.createArtifactRule(artifactId, rule);
        }

        // add new field
        Schema schema2 = new Schema.Parser().parse("{\"namespace\": \"example.avro\"," +
                                                   " \"type\": \"record\"," +
                                                   " \"name\": \"user\"," +
                                                   " \"fields\": [" +
                                                   "     {\"name\": \"name\", \"type\": \"string\"}," +
                                                   "     {\"name\": \"favorite_number\",  \"type\": \"int\"}," +
                                                   "     {\"name\": \"favorite_color\", \"type\": \"string\", \"default\": \"green\"}" +
                                                   " ]" +
                                                   "}");
        File file = new File(tempDirectory, artifactId + ".avsc");
        writeContent(file, schema2.toString().getBytes());

        mojo.artifacts = Collections.singletonMap(artifactId, file);
        mojo.artifactType = ArtifactType.AVRO;
        mojo.execute();

        Assertions.assertTrue(mojo.compatibility.get(artifactId));
    }

}
