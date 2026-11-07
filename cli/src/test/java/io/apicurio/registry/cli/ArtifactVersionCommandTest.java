package io.apicurio.registry.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.apicurio.registry.rest.v3.beans.SearchedVersion;
import io.apicurio.registry.rest.v3.beans.VersionMetaData;
import io.apicurio.registry.rest.v3.beans.VersionSearchResults;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static io.apicurio.registry.cli.common.CliException.SERVER_ERROR_RETURN_CODE;
import static io.apicurio.registry.cli.common.CliException.VALIDATION_ERROR_RETURN_CODE;
import static io.apicurio.registry.cli.utils.Mapper.MAPPER;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the artifact version CLI commands.
 * <p>
 * Every test gets its own group and artifact, created with a single version {@code 1}. Tests that
 * need more versions create them, so any method can run alone (for example as a Surefire rerun).
 */
@QuarkusTest
public class ArtifactVersionCommandTest extends AbstractCLITest {

    private static final String TEST_ARTIFACT = "version-test-artifact";
    private static final String FIRST_VERSION = "1";
    private static final String SECOND_VERSION = "2.0.0";
    private static final String DRAFT_VERSION = "3.0.0-draft";
    private static final String MISSING_VERSION = "9.9.9";

    @TempDir
    Path tempDir;

    private String testGroup;

    @BeforeEach
    @Timeout(120)
    public void setUpArtifact() throws IOException {
        testGroup = "version-test-" + UUID.randomUUID();
        executeAndAssertSuccess("group", "create", testGroup);
        executeAndAssertSuccess("artifact", "create",
                "--group", testGroup, "--type", "JSON",
                "--file", writeSchema("first.json", "{\"type\": \"string\"}"), TEST_ARTIFACT);
        out.getBuffer().setLength(0);
        err.getBuffer().setLength(0);
    }

    @Test
    public void testVersionHelp() {
        testHelpCommand("artifact", "version");
        testHelpCommand("artifact", "version", "create");
        testHelpCommand("artifact", "version", "get");
        testHelpCommand("artifact", "version", "update");
        testHelpCommand("artifact", "version", "delete");
    }

    // -- Error cases --

    @Test
    public void testVersionGetNonExistentVersion() {
        assertExitCode(SERVER_ERROR_RETURN_CODE, "artifact", "version", "get",
                "--group", testGroup, "--artifact", TEST_ARTIFACT, MISSING_VERSION);
    }

    @Test
    public void testVersionDeleteNonExistentVersion() throws JsonProcessingException {
        assertExitCode(SERVER_ERROR_RETURN_CODE, "artifact", "version", "delete",
                "--group", testGroup, "--artifact", TEST_ARTIFACT, MISSING_VERSION);
        assertThat(listVersions())
                .as(withCliOutput("A failed delete should leave the existing version alone"))
                .containsExactly(FIRST_VERSION);
    }

    @Test
    public void testVersionUpdateNonExistentVersion() {
        assertExitCode(SERVER_ERROR_RETURN_CODE, "artifact", "version", "update",
                "--group", testGroup, "--artifact", TEST_ARTIFACT,
                "--name", "New Name", MISSING_VERSION);
    }

    @Test
    public void testVersionCreateMissingArtifact() throws Exception {
        // No --artifact and no context artifactId should fail
        assertExitCode(VALIDATION_ERROR_RETURN_CODE, "artifact", "version", "create",
                "--group", testGroup, "--file", writeSchema("empty.tmp", ""));
        assertThat(err.toString())
                .as(withCliOutput("The error should say the artifact ID is required"))
                .contains("Artifact ID is required");
    }

    @Test
    public void testVersionUpdateNoOptions() {
        assertExitCode(VALIDATION_ERROR_RETURN_CODE, "artifact", "version", "update",
                "--group", testGroup, "--artifact", TEST_ARTIFACT, FIRST_VERSION);
        assertThat(err.toString())
                .as(withCliOutput("The error should say an update option is required"))
                .contains("At least one update option is required");
    }

    @Test
    public void testVersionListNonExistentGroup() {
        assertExitCode(SERVER_ERROR_RETURN_CODE, "artifact", "version",
                "--group", "missing-" + UUID.randomUUID(), "--artifact", TEST_ARTIFACT,
                "--output-type", "json");
    }

    // -- Success cases --

    @Test
    public void testVersionListCommand() throws JsonProcessingException {
        // The artifact was created with an initial version
        executeAndAssertSuccess("artifact", "version", "--output-type", "json",
                "--group", testGroup, "--artifact", TEST_ARTIFACT);
        var versions = MAPPER.readValue(out.toString(), VersionSearchResults.class);

        assertThat(versions.getVersions())
                .as(withCliOutput("There should be one version initially."))
                .extracting(SearchedVersion::getVersion)
                .containsExactly(FIRST_VERSION);
    }

    @Test
    public void testVersionCreateCommand() throws Exception {
        executeAndAssertSuccess("artifact", "version", "create", "--output-type", "json",
                "--group", testGroup, "--artifact", TEST_ARTIFACT,
                "--name", "Second Version",
                "--description", "A test version",
                "--file", writeSchema("second.json", "{\"type\": \"integer\"}"),
                SECOND_VERSION);
        var version = MAPPER.readValue(out.toString(), VersionMetaData.class);

        assertThat(version.getVersion())
                .as(withCliOutput("Created version should have the correct version"))
                .isEqualTo(SECOND_VERSION);
        assertThat(version.getName())
                .as(withCliOutput("Created version should have the correct name"))
                .isEqualTo("Second Version");
        assertThat(version.getDescription())
                .as(withCliOutput("Created version should have the correct description"))
                .isEqualTo("A test version");
    }

    @Test
    public void testVersionGetMetadata() throws Exception {
        createSecondVersion();

        executeAndAssertSuccess("artifact", "version", "get", "--output-type", "json",
                "--group", testGroup, "--artifact", TEST_ARTIFACT, SECOND_VERSION);
        var version = MAPPER.readValue(out.toString(), VersionMetaData.class);

        assertThat(version.getVersion())
                .as(withCliOutput("Retrieved version should have the correct version"))
                .isEqualTo(SECOND_VERSION);
        assertThat(version.getName())
                .as(withCliOutput("Retrieved version should have the correct name"))
                .isEqualTo("Second Version");
    }

    @Test
    public void testVersionGetContent() throws Exception {
        createSecondVersion();

        executeAndAssertSuccess("artifact", "version", "get", "--content",
                "--group", testGroup, "--artifact", TEST_ARTIFACT, SECOND_VERSION);

        assertThat(out.toString())
                .as(withCliOutput("Content should contain the JSON schema"))
                .contains("integer");
    }

    @Test
    public void testVersionGetContentAndOutputTypeMutuallyExclusive() {
        executeAndAssertFailure("artifact", "version", "get", "--content", "--output-type", "json",
                "--group", testGroup, "--artifact", TEST_ARTIFACT, FIRST_VERSION);
    }

    @Test
    public void testVersionListWithPagination() throws Exception {
        createSecondVersion();

        executeAndAssertSuccess("artifact", "version", "--output-type", "json",
                "--group", testGroup, "--artifact", TEST_ARTIFACT,
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
    public void testVersionUpdateMetadata() throws Exception {
        createSecondVersion();

        executeAndAssertSuccess("artifact", "version", "update",
                "--group", testGroup, "--artifact", TEST_ARTIFACT,
                "--name", "Updated Version Name",
                "--description", "Updated description",
                "--set-label", "env=test",
                SECOND_VERSION);

        var version = getVersion(SECOND_VERSION);
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
    public void testVersionUpdateState() throws Exception {
        createSecondVersion();

        // Disable the version
        executeAndAssertSuccess("artifact", "version", "update",
                "--group", testGroup, "--artifact", TEST_ARTIFACT,
                "--state", "DISABLED", SECOND_VERSION);
        assertThat(getVersion(SECOND_VERSION).getState().name())
                .as(withCliOutput("Version should be DISABLED"))
                .isEqualTo("DISABLED");

        // Re-enable the version
        executeAndAssertSuccess("artifact", "version", "update",
                "--group", testGroup, "--artifact", TEST_ARTIFACT,
                "--state", "ENABLED", SECOND_VERSION);
        assertThat(getVersion(SECOND_VERSION).getState().name())
                .as(withCliOutput("Version should be ENABLED again"))
                .isEqualTo("ENABLED");
    }

    @Test
    public void testVersionUpdateStateDeprecated() throws Exception {
        createSecondVersion();

        executeAndAssertSuccess("artifact", "version", "update",
                "--group", testGroup, "--artifact", TEST_ARTIFACT,
                "--state", "DEPRECATED", SECOND_VERSION);

        assertThat(getVersion(SECOND_VERSION).getState().name())
                .as(withCliOutput("Version should be DEPRECATED"))
                .isEqualTo("DEPRECATED");

        // Re-enable the version
        executeAndAssertSuccess("artifact", "version", "update",
                "--group", testGroup, "--artifact", TEST_ARTIFACT,
                "--state", "ENABLED", SECOND_VERSION);
        assertThat(getVersion(SECOND_VERSION).getState().name())
                .as(withCliOutput("Version should be ENABLED again"))
                .isEqualTo("ENABLED");
    }

    @Test
    public void testVersionDeleteLabel() throws Exception {
        createSecondVersion();

        // Add a label
        executeAndAssertSuccess("artifact", "version", "update",
                "--group", testGroup, "--artifact", TEST_ARTIFACT,
                "--set-label", "toremove=value", SECOND_VERSION);
        assertThat(getVersion(SECOND_VERSION).getLabels())
                .as(withCliOutput("Label should exist before it is deleted"))
                .containsEntry("toremove", "value");

        // Delete the label
        executeAndAssertSuccess("artifact", "version", "update",
                "--group", testGroup, "--artifact", TEST_ARTIFACT,
                "--delete-label", "toremove", SECOND_VERSION);

        assertThat(getVersion(SECOND_VERSION).getLabels())
                .as(withCliOutput("Deleted label should no longer exist"))
                .doesNotContainKey("toremove");
    }

    @Test
    public void testVersionCreateDraft() throws Exception {
        executeAndAssertSuccess("artifact", "version", "create", "--output-type", "json",
                "--group", testGroup, "--artifact", TEST_ARTIFACT,
                "--draft",
                "--file", writeSchema("draft.json", "{\"type\": \"boolean\"}"),
                DRAFT_VERSION);
        var version = MAPPER.readValue(out.toString(), VersionMetaData.class);

        assertThat(version.getState().name())
                .as(withCliOutput("Draft version should be in DRAFT state"))
                .isEqualTo("DRAFT");
    }

    @Test
    public void testVersionUpdateDraftContent() throws Exception {
        executeAndAssertSuccess("artifact", "version", "create",
                "--group", testGroup, "--artifact", TEST_ARTIFACT,
                "--draft",
                "--file", writeSchema("draft.json", "{\"type\": \"boolean\"}"),
                DRAFT_VERSION);

        // Update the draft version's content
        executeAndAssertSuccess("artifact", "version", "update",
                "--group", testGroup, "--artifact", TEST_ARTIFACT,
                "--file", writeSchema("updated-draft.json", "{\"type\": \"number\"}"), DRAFT_VERSION);

        // Verify content was updated
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "version", "get", "--content",
                "--group", testGroup, "--artifact", TEST_ARTIFACT, DRAFT_VERSION);
        assertThat(out.toString())
                .as(withCliOutput("Updated draft content should contain 'number'"))
                .contains("number")
                .doesNotContain("boolean");
    }

    @Test
    public void testVersionUpdateContentNonDraft() throws Exception {
        createSecondVersion();

        // Updating content on a non-draft version should fail
        assertExitCode(SERVER_ERROR_RETURN_CODE, "artifact", "version", "update",
                "--group", testGroup, "--artifact", TEST_ARTIFACT,
                "--file", writeSchema("non-draft.json", "{\"type\": \"string\"}"), SECOND_VERSION);

        // And leave the content as it was
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "version", "get", "--content",
                "--group", testGroup, "--artifact", TEST_ARTIFACT, SECOND_VERSION);
        assertThat(out.toString())
                .as(withCliOutput("Rejected update should leave the content unchanged"))
                .contains("integer");
    }

    @Test
    public void testVersionDelete() throws Exception {
        createSecondVersion();

        executeAndAssertSuccess("artifact", "version", "delete",
                "--group", testGroup, "--artifact", TEST_ARTIFACT, SECOND_VERSION);

        assertThat(listVersions())
                .as(withCliOutput("Only the first version should remain after the delete"))
                .containsExactly(FIRST_VERSION);
    }

    @Test
    public void testVersionUsesArtifactIdFromContext() throws Exception {
        // Create context with artifactId
        executeAndAssertSuccess("context", "delete", "--all");
        executeAndAssertSuccess("context", "create", "test-with-artifact", registryUrl,
                "--group", testGroup, "--artifact", TEST_ARTIFACT);

        // List versions without --artifact (should use context)
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "version", "--output-type", "json");
        var versions = MAPPER.readValue(out.toString(), VersionSearchResults.class);

        assertThat(versions.getVersions())
                .as(withCliOutput("Should find versions using artifactId from context"))
                .extracting(SearchedVersion::getVersion)
                .containsExactly(FIRST_VERSION);
    }

    private void createSecondVersion() throws IOException {
        executeAndAssertSuccess("artifact", "version", "create",
                "--group", testGroup, "--artifact", TEST_ARTIFACT,
                "--name", "Second Version",
                "--description", "A test version",
                "--file", writeSchema("second.json", "{\"type\": \"integer\"}"),
                SECOND_VERSION);
        out.getBuffer().setLength(0);
    }

    private List<String> listVersions() throws JsonProcessingException {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "version", "--output-type", "json",
                "--group", testGroup, "--artifact", TEST_ARTIFACT);
        return MAPPER.readValue(out.toString(), VersionSearchResults.class).getVersions().stream()
                .map(SearchedVersion::getVersion)
                .toList();
    }

    // Asserts the exact exit code, so that a failure for an unrelated reason (such as a lost
    // connection, which exits with APPLICATION_ERROR_RETURN_CODE) does not pass the test.
    private void assertExitCode(final int expected, final String... command) {
        assertThat(cmd.execute(command))
                .as(withCliOutput("Command '" + String.join(" ", command) + "' exit code"))
                .isEqualTo(expected);
    }

    private VersionMetaData getVersion(final String version) throws JsonProcessingException {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "version", "get", "--output-type", "json",
                "--group", testGroup, "--artifact", TEST_ARTIFACT, version);
        return MAPPER.readValue(out.toString(), VersionMetaData.class);
    }

    private String writeSchema(final String fileName, final String content) throws IOException {
        return Files.writeString(tempDir.resolve(fileName), content).toString();
    }
}
