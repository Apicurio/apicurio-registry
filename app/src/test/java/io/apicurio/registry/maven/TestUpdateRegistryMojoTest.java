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
import java.util.Collections;

import org.apache.avro.Schema;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.rest.beans.Rule;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.quarkus.test.junit.QuarkusTest;

/**
 * @author Ales Justin
 */
@QuarkusTest
public class TestUpdateRegistryMojoTest extends RegistryMojoTestBase {
    TestUpdateRegistryMojo mojo;

    @BeforeEach
    public void createMojo() {
        this.mojo = new TestUpdateRegistryMojo();
        this.mojo.registryUrl = "http://localhost:8081/api";
    }

    @Test
    public void testCompatibility() throws Exception {
        String artifactId = generateArtifactId();

        Schema schema = new Schema.Parser().parse("{\"namespace\": \"example.avro\"," +
                                                  " \"type\": \"record\"," +
                                                  " \"name\": \"user\"," +
                                                  " \"fields\": [" +
                                                  "     {\"name\": \"name\", \"type\": \"string\"}," +
                                                  "     {\"name\": \"favorite_number\",  \"type\": \"int\"}" +
                                                  " ]" +
                                                  "}");
        client.createArtifact(artifactId, ArtifactType.AVRO, new ByteArrayInputStream(schema.toString().getBytes(StandardCharsets.UTF_8)));
        
        this.waitForArtifact(artifactId);

        Rule rule = new Rule();
        rule.setType(RuleType.COMPATIBILITY);
        rule.setConfig("BACKWARD");
        client.createArtifactRule(artifactId, rule);

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
        writeContent(file, schema2.toString().getBytes(StandardCharsets.UTF_8));

        mojo.artifacts = Collections.singletonMap(artifactId, file);
        mojo.artifactType = ArtifactType.AVRO;
        mojo.execute();

        Assertions.assertTrue(mojo.results.get(artifactId));
    }

}
