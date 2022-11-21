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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import io.apicurio.registry.maven.TestArtifact;
import io.apicurio.registry.maven.TestUpdateRegistryMojo;
import org.apache.avro.Schema;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.rest.v2.beans.Rule;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.tests.TestUtils;
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
        this.mojo.setRegistryUrl(TestUtils.getRegistryV2ApiUrl(testPort));
    }

    @Test
    public void testCompatibility() throws Exception {
        String groupId = TestUpdateRegistryMojoTest.class.getName();
        String artifactId = generateArtifactId();

        Schema schema = new Schema.Parser().parse("{\"namespace\": \"example.avro\"," +
                                                  " \"type\": \"record\"," +
                                                  " \"name\": \"user\"," +
                                                  " \"fields\": [" +
                                                  "     {\"name\": \"name\", \"type\": \"string\"}," +
                                                  "     {\"name\": \"favorite_number\",  \"type\": \"int\"}" +
                                                  " ]" +
                                                  "}");
        clientV2.createArtifact(groupId, artifactId, ArtifactType.AVRO, new ByteArrayInputStream(schema.toString().getBytes(StandardCharsets.UTF_8)));
        this.waitForArtifact(groupId, artifactId);

        Rule rule = new Rule();
        rule.setType(RuleType.COMPATIBILITY);
        rule.setConfig("BACKWARD");
        clientV2.createArtifactRule(groupId, artifactId, rule);

        // Wait for the rule configuration to be set.
        TestUtils.retry(() -> {
            Rule rconfig = clientV2.getArtifactRuleConfig(groupId, artifactId, RuleType.COMPATIBILITY);
            Assertions.assertEquals("BACKWARD", rconfig.getConfig());
        });

        // add new field
        Schema schema2 = new Schema.Parser().parse("{\"namespace\": \"example.avro\"," +
                                                   " \"type\": \"record\"," +
                                                   " \"name\": \"user\"," +
                                                   " \"fields\": [" +
                                                   "     {\"name\": \"name\", \"type\": \"string\"}," +
                                                   "     {\"name\": \"favorite_number\",  \"type\": \"string\"}," +
                                                   "     {\"name\": \"favorite_color\", \"type\": \"string\", \"default\": \"green\"}" +
                                                   " ]" +
                                                   "}");
        File file = new File(tempDirectory, artifactId + ".avsc");
        writeContent(file, schema2.toString().getBytes(StandardCharsets.UTF_8));

        List<TestArtifact> artifacts = new ArrayList<>();
        TestArtifact artifact = new TestArtifact();
        artifact.setGroupId(groupId);
        artifact.setArtifactId(artifactId);
        artifact.setFile(file);
        artifacts.add(artifact);

        mojo.setArtifacts(artifacts);

        Assertions.assertThrows(MojoExecutionException.class, () -> {
            mojo.execute();
        });
    }

}
