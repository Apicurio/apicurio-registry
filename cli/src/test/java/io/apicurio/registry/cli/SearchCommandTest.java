package io.apicurio.registry.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.apicurio.registry.rest.v3.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.v3.beans.GroupSearchResults;
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
public class SearchCommandTest extends AbstractCLITest {

    private static final String TEST_GROUP = "search-test-group";
    private static final String TEST_ARTIFACT = "search-test-artifact";

    @Test
    @Order(0)
    public void testSetup() throws Exception {
        executeAndAssertSuccess("group", "create", TEST_GROUP);
        final Path tempFile = Files.createTempFile("search-test", ".json");
        Files.writeString(tempFile, "{\"type\": \"string\"}");
        try {
            executeAndAssertSuccess("artifact", "create", "-g", TEST_GROUP,
                    "--type", "JSON", "--file", tempFile.toString(),
                    "-l", "env=test", TEST_ARTIFACT);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    @Order(1)
    public void testSearchHelp() {
        testHelpCommand("search");
        testHelpCommand("search", "group");
        testHelpCommand("search", "artifact");
        testHelpCommand("search", "version");
    }

    @Test
    @Order(2)
    public void testSearchGroups() throws JsonProcessingException {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("search", "groups", "--output-type", "json");
        var results = MAPPER.readValue(out.toString(), GroupSearchResults.class);

        assertThat(results.getGroups())
                .as(withCliOutput("Search should return at least the test group"))
                .isNotEmpty();
    }

    @Test
    @Order(3)
    public void testSearchGroupsWithFilter() throws JsonProcessingException {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("search", "groups", "-g", TEST_GROUP, "--output-type", "json");
        var results = MAPPER.readValue(out.toString(), GroupSearchResults.class);

        assertThat(results.getGroups())
                .as(withCliOutput("Filtered search should return only matching groups"))
                .isNotEmpty()
                .allMatch(g -> g.getGroupId().contains(TEST_GROUP));
    }

    @Test
    @Order(4)
    public void testSearchGroupsNoResults() throws JsonProcessingException {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("search", "groups", "-g", "non-existent-group-xyz", "--output-type", "json");
        var results = MAPPER.readValue(out.toString(), GroupSearchResults.class);

        assertThat(results.getGroups())
                .as(withCliOutput("Search for non-existent group should return empty"))
                .isEmpty();
    }

    @Test
    @Order(5)
    public void testSearchGroupsTableOutput() {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("search", "groups");
        assertThat(out.toString())
                .as(withCliOutput("Table output should contain column headers"))
                .contains("Group ID");
    }

    @Test
    @Order(6)
    public void testSearchGroupsPagination() throws JsonProcessingException {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("search", "groups", "-p", "1", "-s", "1", "--output-type", "json");
        var results = MAPPER.readValue(out.toString(), GroupSearchResults.class);

        assertThat(results.getGroups())
                .as(withCliOutput("Paginated search should return at most 1 result"))
                .hasSizeLessThanOrEqualTo(1);
    }

    @Test
    @Order(7)
    public void testSearchGroupsOrderBy() throws JsonProcessingException {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("search", "groups", "--order-by", "CreatedOn", "--order", "Desc",
                "--output-type", "json");
        var results = MAPPER.readValue(out.toString(), GroupSearchResults.class);

        assertThat(results.getGroups())
                .as(withCliOutput("Order by should return results"))
                .isNotEmpty();
    }

    @Test
    @Order(8)
    public void testSearchGroupsInvalidOrderBy() {
        executeAndAssertFailure("search", "groups", "--order-by", "INVALID");
    }

    @Test
    @Order(9)
    public void testSearchGroupsInvalidOrder() {
        executeAndAssertFailure("search", "groups", "--order", "INVALID");
    }

    @Test
    @Order(10)
    public void testSearchArtifacts() throws JsonProcessingException {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("search", "artifacts", "--output-type", "json");
        var results = MAPPER.readValue(out.toString(), ArtifactSearchResults.class);

        assertThat(results.getArtifacts())
                .as(withCliOutput("Search should return at least the test artifact"))
                .isNotEmpty();
    }

    @Test
    @Order(11)
    public void testSearchArtifactsWithFilter() throws JsonProcessingException {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("search", "artifacts", "-g", TEST_GROUP, "-a", TEST_ARTIFACT,
                "--output-type", "json");
        var results = MAPPER.readValue(out.toString(), ArtifactSearchResults.class);

        assertThat(results.getArtifacts())
                .as(withCliOutput("Filtered search should return only matching artifacts"))
                .isNotEmpty()
                .allMatch(a -> TEST_ARTIFACT.equals(a.getArtifactId()));
    }

    @Test
    @Order(12)
    public void testSearchArtifactsNoResults() throws JsonProcessingException {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("search", "artifacts", "-a", "non-existent-artifact-xyz",
                "--output-type", "json");
        var results = MAPPER.readValue(out.toString(), ArtifactSearchResults.class);

        assertThat(results.getArtifacts())
                .as(withCliOutput("Search for non-existent artifact should return empty"))
                .isEmpty();
    }

    @Test
    @Order(13)
    public void testSearchArtifactsTableOutput() {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("search", "artifacts");
        assertThat(out.toString())
                .as(withCliOutput("Table output should contain column headers"))
                .contains("Group ID")
                .contains("Artifact ID");
    }

    @Test
    @Order(14)
    public void testSearchArtifactsByType() throws JsonProcessingException {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("search", "artifacts", "--type", "JSON", "--output-type", "json");
        var results = MAPPER.readValue(out.toString(), ArtifactSearchResults.class);

        assertThat(results.getArtifacts())
                .as(withCliOutput("Type-filtered search should return JSON artifacts"))
                .allMatch(a -> "JSON".equals(a.getArtifactType()));
    }

    @Test
    @Order(15)
    public void testSearchArtifactsOrderBy() throws JsonProcessingException {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("search", "artifacts", "--order-by", "CreatedOn", "--order", "Desc",
                "--output-type", "json");
        var results = MAPPER.readValue(out.toString(), ArtifactSearchResults.class);

        assertThat(results.getArtifacts())
                .as(withCliOutput("Order by should return results"))
                .isNotEmpty();
    }

    @Test
    @Order(16)
    public void testSearchVersions() throws JsonProcessingException {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("search", "versions", "--output-type", "json");
        var results = MAPPER.readValue(out.toString(), VersionSearchResults.class);

        assertThat(results.getVersions())
                .as(withCliOutput("Search should return at least the test version"))
                .isNotEmpty();
    }

    @Test
    @Order(17)
    public void testSearchVersionsWithFilter() throws JsonProcessingException {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("search", "versions", "-g", TEST_GROUP, "-a", TEST_ARTIFACT,
                "--output-type", "json");
        var results = MAPPER.readValue(out.toString(), VersionSearchResults.class);

        assertThat(results.getVersions())
                .as(withCliOutput("Filtered search should return only matching versions"))
                .isNotEmpty()
                .allMatch(v -> TEST_ARTIFACT.equals(v.getArtifactId()));
    }

    @Test
    @Order(18)
    public void testSearchVersionsNoResults() throws JsonProcessingException {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("search", "versions", "-a", "non-existent-artifact-xyz",
                "--output-type", "json");
        var results = MAPPER.readValue(out.toString(), VersionSearchResults.class);

        assertThat(results.getVersions())
                .as(withCliOutput("Search for non-existent artifact versions should return empty"))
                .isEmpty();
    }

    @Test
    @Order(19)
    public void testSearchVersionsTableOutput() {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("search", "versions");
        assertThat(out.toString())
                .as(withCliOutput("Table output should contain column headers"))
                .contains("Group ID")
                .contains("Artifact ID")
                .contains("Version");
    }

    @Test
    @Order(20)
    public void testSearchVersionsByState() throws JsonProcessingException {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("search", "versions", "--state", "ENABLED", "--output-type", "json");
        var results = MAPPER.readValue(out.toString(), VersionSearchResults.class);

        assertThat(results.getVersions())
                .as(withCliOutput("State-filtered search should return only ENABLED versions"))
                .isNotEmpty()
                .allMatch(v -> io.apicurio.registry.types.VersionState.ENABLED.equals(v.getState()));
    }

    @Test
    @Order(21)
    public void testSearchVersionsByVersion() throws JsonProcessingException {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("search", "versions", "-v", "1", "--output-type", "json");
        var results = MAPPER.readValue(out.toString(), VersionSearchResults.class);

        assertThat(results.getVersions())
                .as(withCliOutput("Version filter should return results"))
                .isNotEmpty();
    }

    @Test
    @Order(22)
    public void testSearchVersionsPagination() throws JsonProcessingException {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("search", "versions", "-p", "1", "-s", "1", "--output-type", "json");
        var results = MAPPER.readValue(out.toString(), VersionSearchResults.class);

        assertThat(results.getVersions())
                .as(withCliOutput("Paginated search should return at most 1 result"))
                .hasSizeLessThanOrEqualTo(1);
    }

    @Test
    @Order(23)
    public void testSearchVersionsOrderBy() throws JsonProcessingException {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("search", "versions", "--order-by", "CreatedOn", "--order", "Desc",
                "--output-type", "json");
        var results = MAPPER.readValue(out.toString(), VersionSearchResults.class);

        assertThat(results.getVersions())
                .as(withCliOutput("Order by should return results"))
                .isNotEmpty();
    }

    @Test
    @Order(24)
    public void testSearchArtifactsByLabel() throws JsonProcessingException {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("search", "artifacts", "-l", "env=test", "--output-type", "json");
        var results = MAPPER.readValue(out.toString(), ArtifactSearchResults.class);

        assertThat(results.getArtifacts())
                .as(withCliOutput("Label-filtered search should return matching artifacts"))
                .isNotEmpty()
                .anyMatch(a -> TEST_ARTIFACT.equals(a.getArtifactId()));
    }

    @Test
    @Order(25)
    public void testSearchArtifactsInvalidLabel() {
        executeAndAssertFailure("search", "artifacts", "-l", ":foo");
    }

    @Test
    @Order(26)
    public void testSearchVersionsInvalidState() {
        executeAndAssertFailure("search", "versions", "--state", "INVALID");
    }
}
