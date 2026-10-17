package io.apicurio.registry.cli;

import com.fasterxml.jackson.databind.JsonNode;
import io.apicurio.registry.rest.client.models.ArtifactReference;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.UUID;

import static io.apicurio.registry.cli.common.CliException.SERVER_ERROR_RETURN_CODE;
import static io.apicurio.registry.cli.common.CliException.VALIDATION_ERROR_RETURN_CODE;
import static io.apicurio.registry.cli.utils.Mapper.MAPPER;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the reference CLI commands.
 * <p>
 * Every test gets its own group, holding a target artifact and a source artifact that references
 * it, so any method can run alone (for example as a Surefire rerun).
 */
@QuarkusTest
public class ReferenceCommandTest extends AbstractCLITest {

    private static final String TARGET_ARTIFACT = "ref-target-artifact";
    private static final String SOURCE_ARTIFACT = "ref-source-artifact";
    private static final String REF_NAME = "target-ref";

    private String testGroup;
    private String targetVersion;

    @BeforeEach
    @Timeout(120)
    public void setUpReference() {
        testGroup = "ref-test-" + UUID.randomUUID();
        executeAndAssertSuccess("group", "create", testGroup);

        final var registryClient = client.getRegistryClient();

        final var targetArtifact = new CreateArtifact();
        targetArtifact.setArtifactId(TARGET_ARTIFACT);
        targetArtifact.setArtifactType("JSON");
        final var targetFirstVersion = new CreateVersion();
        final var targetContent = new VersionContent();
        targetContent.setContent("{\"type\": \"string\"}");
        targetContent.setContentType("application/json");
        targetFirstVersion.setContent(targetContent);
        targetArtifact.setFirstVersion(targetFirstVersion);
        final var targetResponse = registryClient.groups().byGroupId(testGroup)
                .artifacts().post(targetArtifact);
        targetVersion = targetResponse.getVersion().getVersion();

        final var ref = new ArtifactReference();
        ref.setGroupId(testGroup);
        ref.setArtifactId(TARGET_ARTIFACT);
        ref.setVersion(targetVersion);
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
        registryClient.groups().byGroupId(testGroup).artifacts().post(sourceArtifact);

        out.getBuffer().setLength(0);
        err.getBuffer().setLength(0);
    }

    // -- Help --

    @Test
    public void testReferenceHelp() {
        testHelpCommand("reference");
        testHelpCommand("reference", "list");
    }

    // -- List references --

    @Test
    public void testListOutboundTable() {
        executeAndAssertSuccess("reference", "list",
                "-g", testGroup, "-a", SOURCE_ARTIFACT, "branch=latest");
        assertThat(out.toString())
                .as(withCliOutput("Should show the target artifact reference"))
                .contains(TARGET_ARTIFACT)
                .contains(REF_NAME);
    }

    @Test
    public void testListOutboundJson() throws Exception {
        executeAndAssertSuccess("reference", "list", "-o", "json",
                "-g", testGroup, "-a", SOURCE_ARTIFACT, "branch=latest");
        JsonNode refs = MAPPER.readTree(out.toString());
        assertThat(refs.isArray())
                .as(withCliOutput("JSON output should be an array"))
                .isTrue();
        assertThat(refs.size())
                .as(withCliOutput("The source artifact has exactly one reference"))
                .isEqualTo(1);
        final var ref = refs.get(0);
        assertThat(ref.get("groupId").asText())
                .as(withCliOutput("Reference should point to the test group"))
                .isEqualTo(testGroup);
        assertThat(ref.get("artifactId").asText())
                .as(withCliOutput("Reference should point to the target artifact"))
                .isEqualTo(TARGET_ARTIFACT);
        assertThat(ref.get("version").asText())
                .as(withCliOutput("Reference should point to the target version"))
                .isEqualTo(targetVersion);
        assertThat(ref.get("name").asText())
                .as(withCliOutput("Reference should carry its name"))
                .isEqualTo(REF_NAME);
    }

    @Test
    public void testListInbound() {
        executeAndAssertSuccess("reference", "list", "--inbound",
                "-g", testGroup, "-a", TARGET_ARTIFACT, "branch=latest");
        assertThat(out.toString())
                .as(withCliOutput("Should show the source artifact as inbound reference"))
                .contains(SOURCE_ARTIFACT);
    }

    @Test
    public void testListOutboundEmpty() {
        executeAndAssertSuccess("reference", "list",
                "-g", testGroup, "-a", TARGET_ARTIFACT, "branch=latest");
        assertThat(out.toString())
                .as(withCliOutput("The target artifact references nothing"))
                .contains("No references found.");
    }

    // -- Error cases --

    @Test
    public void testListNonExistentArtifact() {
        assertThat(cmd.execute("reference", "list",
                "-g", testGroup, "-a", "non-existent", "branch=latest"))
                .as(withCliOutput("A missing artifact is a server error"))
                .isEqualTo(SERVER_ERROR_RETURN_CODE);
        assertThat(err.toString())
                .as(withCliOutput("The error should name the missing artifact"))
                .contains("non-existent");
    }

    @Test
    public void testListMissingArtifactNoContext() {
        assertThat(cmd.execute("reference", "list",
                "-g", testGroup, "branch=latest"))
                .as(withCliOutput("A missing artifact ID is a validation error"))
                .isEqualTo(VALIDATION_ERROR_RETURN_CODE);
        assertThat(err.toString())
                .as(withCliOutput("The error should say the artifact ID is required"))
                .contains("Artifact ID is required");
    }
}
