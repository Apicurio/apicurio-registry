package io.apicurio.registry.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.apicurio.registry.rest.v3.beans.VersionMetaData;
import io.apicurio.registry.rest.v3.beans.VersionSearchResults;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.nio.file.Files;
import java.nio.file.Path;

import static io.apicurio.registry.cli.utils.Mapper.MAPPER;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestMethodOrder(OrderAnnotation.class)
public class ArtifactVersionCommandTest extends AbstractCLITest {

    // -- Unordered tests (no dependency on state) --

    @Test
    public void testVersionHelp() {
        testHelpCommand("artifact", "version");
        testHelpCommand("artifact", "version", "create");
        testHelpCommand("artifact", "version", "get");
        testHelpCommand("artifact", "version", "update");
        testHelpCommand("artifact", "version", "delete");
    }

    @Test
    public void testVersionGetNonExistentVersion() {
        executeAndAssertFailure("artifact", "version", "get",
                "--group", "default", "--artifact", "non-existent", "1.0.0");
    }

    @Test
    public void testVersionDeleteNonExistentVersion() {
        executeAndAssertFailure("artifact", "version", "delete",
                "--group", "default", "--artifact", "non-existent", "1.0.0");
    }

    @Test
    public void testVersionUpdateNonExistentVersion() {
        executeAndAssertFailure("artifact", "version", "update",
                "--group", "default", "--artifact", "non-existent",
                "--name", "New Name", "1.0.0");
    }

    @Test
    public void testVersionCreateMissingArtifact() throws Exception {
        // No --artifact and no context artifactId should fail
        Path tempFile = Files.createTempFile("version-create-missing-artifact", ".tmp");
        try {
            executeAndAssertFailure("artifact", "version", "create",
                    "--group", "default", "--file", tempFile.toString());
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    public void testVersionUpdateNoOptions() {
        executeAndAssertFailure("artifact", "version", "update",
                "--group", "default", "--artifact", "test", "1.0.0");
    }

    @Test
    public void testVersionListNonExistentGroup() {
        executeAndAssertFailure("artifact", "version",
                "--group", "non-existent-group", "--artifact", "test", "--output-type", "json");
    }

    // -- Ordered tests (depend on state from previous tests) --

    @Test
    @Order(0)
    public void testSetupArtifact() throws Exception {
        // Create an artifact to use for version tests
        Path tempFile = Files.createTempFile("version-test-schema", ".json");
        Files.writeString(tempFile, """
                {"type": "string"}
                """);
        try {
            executeAndAssertSuccess("artifact", "create",
                    "--group", "default", "--type", "JSON",
                    "--file", tempFile.toString(), "version-test-artifact");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    @Order(1)
    public void testVersionListCommand() throws JsonProcessingException {
        // The artifact was created with an initial version
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "version", "--output-type", "json",
                "--group", "default", "--artifact", "version-test-artifact");
        var versions = MAPPER.readValue(out.toString(), VersionSearchResults.class);

        assertThat(versions.getVersions())
                .as(withCliOutput("There should be one version initially."))
                .hasSize(1);
    }

    @Test
    @Order(2)
    public void testVersionCreateCommand() throws Exception {
        Path tempFile = Files.createTempFile("version-test-content", ".json");
        Files.writeString(tempFile, """
                {"type": "integer"}
                """);

        try {
            out.getBuffer().setLength(0);
            executeAndAssertSuccess("artifact", "version", "create", "--output-type", "json",
                    "--group", "default", "--artifact", "version-test-artifact",
                    "--name", "Second Version",
                    "--description", "A test version",
                    "--file", tempFile.toString(),
                    "2.0.0");
            var version = MAPPER.readValue(out.toString(), VersionMetaData.class);

            assertThat(version.getVersion())
                    .as(withCliOutput("Created version should have the correct version"))
                    .isEqualTo("2.0.0");
            assertThat(version.getName())
                    .as(withCliOutput("Created version should have the correct name"))
                    .isEqualTo("Second Version");
            assertThat(version.getDescription())
                    .as(withCliOutput("Created version should have the correct description"))
                    .isEqualTo("A test version");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    @Order(3)
    public void testVersionGetMetadata() throws JsonProcessingException {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "version", "get", "--output-type", "json",
                "--group", "default", "--artifact", "version-test-artifact", "2.0.0");
        var version = MAPPER.readValue(out.toString(), VersionMetaData.class);

        assertThat(version.getVersion())
                .as(withCliOutput("Retrieved version should have the correct version"))
                .isEqualTo("2.0.0");
        assertThat(version.getName())
                .as(withCliOutput("Retrieved version should have the correct name"))
                .isEqualTo("Second Version");
    }

    @Test
    @Order(4)
    public void testVersionGetContent() {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "version", "get", "--content",
                "--group", "default", "--artifact", "version-test-artifact", "2.0.0");
        var content = out.toString();

        assertThat(content)
                .as(withCliOutput("Content should contain the JSON schema"))
                .contains("integer");
    }

    @Test
    @Order(5)
    public void testVersionGetContentAndOutputTypeMutuallyExclusive() {
        executeAndAssertFailure("artifact", "version", "get", "--content", "--output-type", "json",
                "--group", "default", "--artifact", "version-test-artifact", "2.0.0");
    }

    @Test
    @Order(6)
    public void testVersionListWithPagination() throws JsonProcessingException {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "version", "--output-type", "json",
                "--group", "default", "--artifact", "version-test-artifact",
                "-p", "1", "-s", "1");
        var versions = MAPPER.readValue(out.toString(), VersionSearchResults.class);

        assertThat(versions.getVersions())
                .as(withCliOutput("Page 1 with size 1 should return one version."))
                .hasSize(1);
        assertThat(versions.getCount())
                .as(withCliOutput("Total count should be 2."))
                .isEqualTo(2);
    }

    @Test
    @Order(7)
    public void testVersionUpdateMetadata() throws JsonProcessingException {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "version", "update",
                "--group", "default", "--artifact", "version-test-artifact",
                "--name", "Updated Version Name",
                "--description", "Updated description",
                "--set-label", "env=test",
                "2.0.0");

        // Verify
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "version", "get", "--output-type", "json",
                "--group", "default", "--artifact", "version-test-artifact", "2.0.0");
        var version = MAPPER.readValue(out.toString(), VersionMetaData.class);
        assertThat(version.getName())
                .as(withCliOutput("Updated version should have the new name"))
                .isEqualTo("Updated Version Name");
        assertThat(version.getDescription())
                .as(withCliOutput("Updated version should have the new description"))
                .isEqualTo("Updated description");
        assertThat(version.getLabels())
                .as(withCliOutput("Updated version should have the new label"))
                .containsEntry("env", "test");
    }

    @Test
    @Order(8)
    public void testVersionUpdateState() throws JsonProcessingException {
        // Disable the version
        executeAndAssertSuccess("artifact", "version", "update",
                "--group", "default", "--artifact", "version-test-artifact",
                "--state", "DISABLED", "2.0.0");

        // Verify state changed
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "version", "get", "--output-type", "json",
                "--group", "default", "--artifact", "version-test-artifact", "2.0.0");
        var version = MAPPER.readValue(out.toString(), VersionMetaData.class);
        assertThat(version.getState().name())
                .as(withCliOutput("Version should be DISABLED"))
                .isEqualTo("DISABLED");

        // Re-enable the version
        executeAndAssertSuccess("artifact", "version", "update",
                "--group", "default", "--artifact", "version-test-artifact",
                "--state", "ENABLED", "2.0.0");
    }

    @Test
    @Order(9)
    public void testVersionUpdateStateDeprecated() throws JsonProcessingException {
        executeAndAssertSuccess("artifact", "version", "update",
                "--group", "default", "--artifact", "version-test-artifact",
                "--state", "DEPRECATED", "2.0.0");

        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "version", "get", "--output-type", "json",
                "--group", "default", "--artifact", "version-test-artifact", "2.0.0");
        var version = MAPPER.readValue(out.toString(), VersionMetaData.class);
        assertThat(version.getState().name())
                .as(withCliOutput("Version should be DEPRECATED"))
                .isEqualTo("DEPRECATED");

        // Re-enable
        executeAndAssertSuccess("artifact", "version", "update",
                "--group", "default", "--artifact", "version-test-artifact",
                "--state", "ENABLED", "2.0.0");
    }

    @Test
    @Order(10)
    public void testVersionDeleteLabel() throws JsonProcessingException {
        // Add a label
        executeAndAssertSuccess("artifact", "version", "update",
                "--group", "default", "--artifact", "version-test-artifact",
                "--set-label", "toremove=value", "2.0.0");

        // Delete the label
        executeAndAssertSuccess("artifact", "version", "update",
                "--group", "default", "--artifact", "version-test-artifact",
                "--delete-label", "toremove", "2.0.0");

        // Verify
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "version", "get", "--output-type", "json",
                "--group", "default", "--artifact", "version-test-artifact", "2.0.0");
        var version = MAPPER.readValue(out.toString(), VersionMetaData.class);
        assertThat(version.getLabels())
                .as(withCliOutput("Deleted label should no longer exist"))
                .doesNotContainKey("toremove");
    }

    @Test
    @Order(11)
    public void testVersionCreateDraft() throws Exception {
        Path tempFile = Files.createTempFile("draft-version", ".json");
        Files.writeString(tempFile, """
                {"type": "boolean"}
                """);

        try {
            out.getBuffer().setLength(0);
            executeAndAssertSuccess("artifact", "version", "create", "--output-type", "json",
                    "--group", "default", "--artifact", "version-test-artifact",
                    "--draft",
                    "--file", tempFile.toString(),
                    "3.0.0-draft");
            var version = MAPPER.readValue(out.toString(), VersionMetaData.class);
            assertThat(version.getState().name())
                    .as(withCliOutput("Draft version should be in DRAFT state"))
                    .isEqualTo("DRAFT");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    @Order(12)
    public void testVersionUpdateDraftContent() throws Exception {
        // Update the draft version's content
        Path tempFile = Files.createTempFile("updated-draft-content", ".json");
        Files.writeString(tempFile, """
                {"type": "number"}
                """);

        try {
            executeAndAssertSuccess("artifact", "version", "update",
                    "--group", "default", "--artifact", "version-test-artifact",
                    "--file", tempFile.toString(), "3.0.0-draft");

            // Verify content was updated
            out.getBuffer().setLength(0);
            executeAndAssertSuccess("artifact", "version", "get", "--content",
                    "--group", "default", "--artifact", "version-test-artifact", "3.0.0-draft");
            assertThat(out.toString())
                    .as(withCliOutput("Updated draft content should contain 'number'"))
                    .contains("number");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    @Order(13)
    public void testVersionUpdateContentNonDraft() throws Exception {
        // Updating content on a non-draft version should fail
        Path tempFile = Files.createTempFile("update-content-non-draft", ".json");
        Files.writeString(tempFile, """
                {"type": "string"}
                """);
        try {
            executeAndAssertFailure("artifact", "version", "update",
                    "--group", "default", "--artifact", "version-test-artifact",
                    "--file", tempFile.toString(), "2.0.0");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    @Order(14)
    public void testVersionDelete() throws JsonProcessingException {
        executeAndAssertSuccess("artifact", "version", "delete",
                "--group", "default", "--artifact", "version-test-artifact", "2.0.0");

        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "version", "--output-type", "json",
                "--group", "default", "--artifact", "version-test-artifact");
        var versions = MAPPER.readValue(out.toString(), VersionSearchResults.class);
        assertThat(versions.getVersions())
                .as(withCliOutput("Deleted version should no longer exist"))
                .noneMatch(v -> "2.0.0".equals(v.getVersion()));
    }

    @Test
    @Order(15)
    public void testVersionUsesArtifactIdFromContext() throws Exception {
        // Create context with artifactId
        executeAndAssertSuccess("context", "delete", "--all");
        executeAndAssertSuccess("context", "create", "test-with-artifact", registryUrl,
                "--group", "default", "--artifact", "version-test-artifact");

        // List versions without --artifact (should use context)
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "version", "--output-type", "json");
        var versions = MAPPER.readValue(out.toString(), VersionSearchResults.class);

        assertThat(versions.getVersions())
                .as(withCliOutput("Should find versions using artifactId from context"))
                .isNotEmpty();
    }
}
