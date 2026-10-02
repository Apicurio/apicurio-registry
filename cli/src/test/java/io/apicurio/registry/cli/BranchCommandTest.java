package io.apicurio.registry.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.apicurio.registry.rest.v3.beans.BranchMetaData;
import io.apicurio.registry.rest.v3.beans.BranchSearchResults;
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

/**
 * Tests for the artifact branch CLI commands.
 */
@QuarkusTest
@TestMethodOrder(OrderAnnotation.class)
public class BranchCommandTest extends AbstractCLITest {

    private static final String TEST_GROUP = "branch-test-group-" + System.currentTimeMillis();
    private static final String TEST_ARTIFACT = "branch-test-artifact-" + System.currentTimeMillis();

    private void createArtifactVersion(String version, String type) throws Exception {
        final Path tempFile = Files.createTempFile("branch-test", ".json");
        Files.writeString(tempFile, "{\"type\": \"" + type + "\"}");
        try {
            out.getBuffer().setLength(0);
            executeAndAssertSuccess("artifact", "version", "create", "-g", TEST_GROUP,
                    "-a", TEST_ARTIFACT, "--file", tempFile.toString(), version);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    @Order(0)
    public void testSetup() throws Exception {
        executeAndAssertSuccess("group", "create", TEST_GROUP);
        final Path tempFile = Files.createTempFile("branch-test", ".json");
        Files.writeString(tempFile, "{\"type\": \"string\"}");
        try {
            out.getBuffer().setLength(0);
            executeAndAssertSuccess("artifact", "create", "-g", TEST_GROUP,
                    "--type", "JSON", "--file", tempFile.toString(), "--version", "1.0.0", TEST_ARTIFACT);
        } finally {
            Files.deleteIfExists(tempFile);
        }
        createArtifactVersion("2.0.0", "integer");
        createArtifactVersion("3.0.0", "boolean");
    }

    @Test
    public void testBranchHelp() {
        testHelpCommand("artifact", "branch");
        testHelpCommand("artifact", "branch", "create");
        testHelpCommand("artifact", "branch", "get");
        testHelpCommand("artifact", "branch", "update");
        testHelpCommand("artifact", "branch", "delete");
        testHelpCommand("artifact", "branch", "version");
        testHelpCommand("artifact", "branch", "version", "add");
        testHelpCommand("artifact", "branch", "version", "replace");
    }

    @Test
    @Order(1)
    public void testBranchListContainsSystemBranch() throws JsonProcessingException {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "branch", "-g", TEST_GROUP, "-a", TEST_ARTIFACT,
                "--output-type", "json");
        var branches = MAPPER.readValue(out.toString(), BranchSearchResults.class);

        assertThat(branches.getBranches())
                .as(withCliOutput("Every artifact has a system-defined 'latest' branch."))
                .anyMatch(b -> "latest".equals(b.getBranchId()) && Boolean.TRUE.equals(b.getSystemDefined()));
    }

    @Test
    @Order(2)
    public void testBranchCreate() throws JsonProcessingException {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "branch", "create", "-g", TEST_GROUP, "-a", TEST_ARTIFACT,
                "--description", "Stable 1.x line", "--output-type", "json", "1.x");
        var branch = MAPPER.readValue(out.toString(), BranchMetaData.class);

        assertThat(branch.getBranchId())
                .as(withCliOutput("Created branch should have the requested ID"))
                .isEqualTo("1.x");
        assertThat(branch.getDescription())
                .as(withCliOutput("Created branch should have the requested description"))
                .isEqualTo("Stable 1.x line");
        assertThat(branch.getSystemDefined())
                .as(withCliOutput("A user-created branch is not system-defined"))
                .isFalse();
    }

    @Test
    @Order(3)
    public void testBranchCreateWithInitialVersions() throws JsonProcessingException {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "branch", "create", "-g", TEST_GROUP, "-a", TEST_ARTIFACT,
                "--version", "2.0.0,1.0.0", "2.x");

        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "branch", "version", "-g", TEST_GROUP, "-a", TEST_ARTIFACT,
                "-b", "2.x", "--output-type", "json");
        var versions = MAPPER.readValue(out.toString(), VersionSearchResults.class);

        assertThat(versions.getVersions())
                .as(withCliOutput("Branch should have been created with the two initial versions"))
                .hasSize(2)
                .extracting("version")
                .containsExactlyInAnyOrder("2.0.0", "1.0.0");
    }

    @Test
    @Order(4)
    public void testBranchListWithPagination() throws JsonProcessingException {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "branch", "-g", TEST_GROUP, "-a", TEST_ARTIFACT,
                "--output-type", "json", "-p", "1", "-s", "1");
        var branches = MAPPER.readValue(out.toString(), BranchSearchResults.class);

        assertThat(branches.getBranches())
                .as(withCliOutput("Page 1 with size 1 should return a single branch"))
                .hasSize(1);
        assertThat(branches.getCount())
                .as(withCliOutput("Total count should be 3 (latest, 1.x, 2.x)"))
                .isEqualTo(3);
    }

    @Test
    @Order(5)
    public void testBranchGet() throws JsonProcessingException {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "branch", "get", "-g", TEST_GROUP, "-a", TEST_ARTIFACT,
                "--output-type", "json", "1.x");
        var branch = MAPPER.readValue(out.toString(), BranchMetaData.class);

        assertThat(branch.getBranchId())
                .as(withCliOutput("Retrieved branch should have the correct ID"))
                .isEqualTo("1.x");
        assertThat(branch.getDescription())
                .as(withCliOutput("Retrieved branch should have the correct description"))
                .isEqualTo("Stable 1.x line");
    }

    @Test
    @Order(6)
    public void testBranchUpdate() throws JsonProcessingException {
        executeAndAssertSuccess("artifact", "branch", "update", "-g", TEST_GROUP, "-a", TEST_ARTIFACT,
                "--description", "Updated description", "1.x");

        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "branch", "get", "-g", TEST_GROUP, "-a", TEST_ARTIFACT,
                "--output-type", "json", "1.x");
        var branch = MAPPER.readValue(out.toString(), BranchMetaData.class);
        assertThat(branch.getDescription())
                .as(withCliOutput("Updated branch should have the new description"))
                .isEqualTo("Updated description");
    }

    @Test
    @Order(7)
    public void testBranchVersionAdd() throws JsonProcessingException {
        executeAndAssertSuccess("artifact", "branch", "version", "add", "-g", TEST_GROUP, "-a", TEST_ARTIFACT,
                "-b", "1.x", "1.0.0");

        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "branch", "version", "-g", TEST_GROUP, "-a", TEST_ARTIFACT,
                "-b", "1.x", "--output-type", "json");
        var versions = MAPPER.readValue(out.toString(), VersionSearchResults.class);
        assertThat(versions.getVersions())
                .as(withCliOutput("The added version should be at the tip of the branch"))
                .hasSize(1)
                .extracting("version")
                .containsExactly("1.0.0");
    }

    @Test
    @Order(8)
    public void testBranchVersionReplace() throws JsonProcessingException {
        executeAndAssertSuccess("artifact", "branch", "version", "replace", "-g", TEST_GROUP, "-a", TEST_ARTIFACT,
                "-b", "1.x", "3.0.0", "2.0.0", "1.0.0");

        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "branch", "version", "-g", TEST_GROUP, "-a", TEST_ARTIFACT,
                "-b", "1.x", "--output-type", "json");
        var versions = MAPPER.readValue(out.toString(), VersionSearchResults.class);
        assertThat(versions.getVersions())
                .as(withCliOutput("The branch version list should have been fully replaced"))
                .hasSize(3)
                .extracting("version")
                .containsExactlyInAnyOrder("3.0.0", "2.0.0", "1.0.0");
    }

    @Test
    @Order(9)
    public void testBranchVersionListWithPagination() throws JsonProcessingException {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "branch", "version", "-g", TEST_GROUP, "-a", TEST_ARTIFACT,
                "-b", "1.x", "--output-type", "json", "-p", "1", "-s", "2");
        var versions = MAPPER.readValue(out.toString(), VersionSearchResults.class);
        assertThat(versions.getVersions())
                .as(withCliOutput("Page 1 with size 2 should return two versions"))
                .hasSize(2);
        assertThat(versions.getCount())
                .as(withCliOutput("Total count should be 3"))
                .isEqualTo(3);
    }

    @Test
    @Order(10)
    public void testBranchTableOutput() {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "branch", "-g", TEST_GROUP, "-a", TEST_ARTIFACT);
        assertThat(out.toString())
                .as(withCliOutput("Table output should contain the branch column headers"))
                .contains("Branch ID")
                .contains("System Defined")
                .contains("1.x");
    }

    @Test
    @Order(11)
    public void testBranchUsesContextIds() throws Exception {
        executeAndAssertSuccess("context", "delete", "--all");
        executeAndAssertSuccess("context", "create", "branch-ctx", registryUrl,
                "--group", TEST_GROUP, "--artifact", TEST_ARTIFACT);

        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "branch", "--output-type", "json");
        var branches = MAPPER.readValue(out.toString(), BranchSearchResults.class);
        assertThat(branches.getBranches())
                .as(withCliOutput("Should list branches using groupId/artifactId from context"))
                .anyMatch(b -> "1.x".equals(b.getBranchId()));

        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "branch", "get", "--output-type", "json", "2.x");
        var branch = MAPPER.readValue(out.toString(), BranchMetaData.class);
        assertThat(branch.getBranchId())
                .as(withCliOutput("branch get should resolve IDs from context"))
                .isEqualTo("2.x");
    }

    @Test
    @Order(12)
    public void testBranchDelete() throws JsonProcessingException {
        executeAndAssertSuccess("artifact", "branch", "delete", "-g", TEST_GROUP, "-a", TEST_ARTIFACT, "2.x");

        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "branch", "-g", TEST_GROUP, "-a", TEST_ARTIFACT,
                "--output-type", "json");
        var branches = MAPPER.readValue(out.toString(), BranchSearchResults.class);
        assertThat(branches.getBranches())
                .as(withCliOutput("Deleted branch should no longer be listed"))
                .noneMatch(b -> "2.x".equals(b.getBranchId()));
    }

    @Test
    public void testBranchMissingArtifactId() {
        executeAndAssertFailure("artifact", "branch", "-g", TEST_GROUP);
        executeAndAssertFailure("artifact", "branch", "get", "-g", TEST_GROUP, "1.x");
        executeAndAssertFailure("artifact", "branch", "version", "-g", TEST_GROUP, "-b", "latest");
    }

    @Test
    public void testBranchNonExistent() {
        executeAndAssertFailure("artifact", "branch", "get",
                "-g", TEST_GROUP, "-a", TEST_ARTIFACT, "does-not-exist");
        executeAndAssertFailure("artifact", "branch", "-g", "non-existent-group", "-a", TEST_ARTIFACT);
        executeAndAssertFailure("artifact", "branch", "version", "add",
                "-g", TEST_GROUP, "-a", TEST_ARTIFACT, "-b", "does-not-exist", "1.0.0");
    }

    @Test
    public void testBranchUpdateNoOptions() {
        executeAndAssertFailure("artifact", "branch", "update",
                "-g", TEST_GROUP, "-a", TEST_ARTIFACT, "1.x");
    }
}
