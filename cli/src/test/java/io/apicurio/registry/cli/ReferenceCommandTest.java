package io.apicurio.registry.cli;

import com.fasterxml.jackson.databind.JsonNode;
import io.apicurio.registry.rest.client.models.ArtifactReference;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;

import static io.apicurio.registry.cli.utils.Mapper.MAPPER;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestMethodOrder(OrderAnnotation.class)
public class ReferenceCommandTest extends AbstractCLITest {

    private static final String TEST_GROUP = "ref-test-group";
    private static final String TARGET_ARTIFACT = "ref-target-artifact";
    private static final String SOURCE_ARTIFACT = "ref-source-artifact";
    private static final String REF_NAME = "target-ref";

    // -- Help --

    @Test
    @Order(0)
    public void testReferenceHelp() {
        testHelpCommand("reference");
        testHelpCommand("reference", "list");
    }

    // -- Setup: create artifacts with an explicit reference --

    @Test
    @Order(1)
    public void testSetup() {
        executeAndAssertSuccess("group", "create", TEST_GROUP);

        final var registryClient = client.getRegistryClient();

        final var targetArtifact = new CreateArtifact();
        targetArtifact.setArtifactId(TARGET_ARTIFACT);
        targetArtifact.setArtifactType("JSON");
        final var targetVersion = new CreateVersion();
        final var targetContent = new VersionContent();
        targetContent.setContent("{\"type\": \"string\"}");
        targetContent.setContentType("application/json");
        targetVersion.setContent(targetContent);
        targetArtifact.setFirstVersion(targetVersion);
        final var targetResponse = registryClient.groups().byGroupId(TEST_GROUP)
                .artifacts().post(targetArtifact);

        final var ref = new ArtifactReference();
        ref.setGroupId(TEST_GROUP);
        ref.setArtifactId(TARGET_ARTIFACT);
        ref.setVersion(targetResponse.getVersion().getVersion());
        ref.setName(REF_NAME);

        final var sourceArtifact = new CreateArtifact();
        sourceArtifact.setArtifactId(SOURCE_ARTIFACT);
        sourceArtifact.setArtifactType("JSON");
        final var sourceVersion = new CreateVersion();
        final var sourceContent = new VersionContent();
        sourceContent.setContent("{\"type\": \"object\", \"properties\": {\"name\": {\"$ref\": \"" + REF_NAME + "\"}}}");
        sourceContent.setContentType("application/json");
        sourceContent.setReferences(List.of(ref));
        sourceVersion.setContent(sourceContent);
        sourceArtifact.setFirstVersion(sourceVersion);
        registryClient.groups().byGroupId(TEST_GROUP).artifacts().post(sourceArtifact);
    }

    // -- List references --

    @Test
    @Order(2)
    public void testListOutboundTable() {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("reference", "list",
                "-g", TEST_GROUP, "-a", SOURCE_ARTIFACT, "branch=latest");
        assertThat(out.toString())
                .as(withCliOutput("Should show the target artifact reference"))
                .contains(TARGET_ARTIFACT);
    }

    @Test
    @Order(3)
    public void testListOutboundJson() throws Exception {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("reference", "list", "-o", "json",
                "-g", TEST_GROUP, "-a", SOURCE_ARTIFACT, "branch=latest");
        JsonNode refs = MAPPER.readTree(out.toString());
        assertThat(refs.isArray())
                .as(withCliOutput("JSON output should be an array"))
                .isTrue();
        assertThat(refs.size())
                .as(withCliOutput("Should contain at least one reference"))
                .isGreaterThan(0);
        boolean found = false;
        for (JsonNode ref : refs) {
            if (TARGET_ARTIFACT.equals(ref.get("artifactId").asText())
                    && REF_NAME.equals(ref.get("name").asText())) {
                found = true;
                break;
            }
        }
        assertThat(found)
                .as(withCliOutput("Should contain reference to " + TARGET_ARTIFACT))
                .isTrue();
    }

    @Test
    @Order(4)
    public void testListInbound() {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("reference", "list", "--inbound",
                "-g", TEST_GROUP, "-a", TARGET_ARTIFACT, "branch=latest");
        assertThat(out.toString())
                .as(withCliOutput("Should show the source artifact as inbound reference"))
                .contains(SOURCE_ARTIFACT);
    }

    // -- Error cases --

    @Test
    @Order(5)
    public void testListNonExistentArtifact() {
        executeAndAssertFailure("reference", "list",
                "-g", TEST_GROUP, "-a", "non-existent", "branch=latest");
    }

    @Test
    @Order(6)
    public void testListMissingArtifactNoContext() {
        executeAndAssertFailure("reference", "list",
                "-g", TEST_GROUP, "branch=latest");
    }
}
