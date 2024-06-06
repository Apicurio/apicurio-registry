package io.apicurio.registry.noprofile.maven;

import io.apicurio.registry.maven.TestArtifact;
import io.apicurio.registry.maven.TestUpdateRegistryMojo;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateRule;
import io.apicurio.registry.rest.client.models.Rule;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.avro.Schema;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

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
        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.AVRO, schema.toString(), ContentTypes.APPLICATION_JSON);
        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);

        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.COMPATIBILITY);
        createRule.setConfig("BACKWARD");
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).rules().post(createRule);

        // Wait for the rule configuration to be set.
        TestUtils.retry(() -> {
            Rule rconfig = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).rules().byRuleType(RuleType.COMPATIBILITY.getValue()).get();
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
