package io.apicurio.registry.noprofile.maven;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.apicurio.registry.maven.TestArtifact;
import io.apicurio.registry.maven.TestUpdateRegistryMojo;
import org.apache.avro.Schema;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.rest.client.models.ArtifactContent;
import io.apicurio.registry.rest.client.models.ArtifactMetaData;
import io.apicurio.registry.rest.client.models.Rule;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class TestUpdateRegistryMojoTest extends RegistryMojoTestBase {
    TestUpdateRegistryMojo mojo;

    @BeforeEach
    public void createMojo() {
        this.mojo = new TestUpdateRegistryMojo();
        this.mojo.setRegistryUrl(TestUtils.getRegistryV3ApiUrl(testPort));
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
        ArtifactContent content = new ArtifactContent();
        content.setContent(schema.toString());
        ArtifactMetaData meta = clientV3.groups().byGroupId(groupId).artifacts().post(content, config -> {
            config.headers.add("X-Registry-ArtifactId", artifactId);
            config.headers.add("X-Registry-ArtifactType", ArtifactType.AVRO);
        }).get(3, TimeUnit.SECONDS);

        Rule rule = new Rule();
        rule.setType(RuleType.COMPATIBILITY);
        rule.setConfig("BACKWARD");
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).rules().post(rule).get(2, TimeUnit.SECONDS);

        // Wait for the rule configuration to be set.
        TestUtils.retry(() -> {
            Rule rconfig = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).rules().byRule(RuleType.COMPATIBILITY.getValue()).get().get(3, TimeUnit.SECONDS);
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
