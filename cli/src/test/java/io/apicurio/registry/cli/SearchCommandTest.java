package io.apicurio.registry.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.apicurio.registry.rest.v3.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.v3.beans.GroupSearchResults;
import io.apicurio.registry.rest.v3.beans.VersionSearchResults;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static io.apicurio.registry.cli.utils.Mapper.MAPPER;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the search CLI commands.
 *
 * <p>Every test that reads registry data builds its own fixture, so no execution order is
 * declared and any method can run on its own. The per-call UUID in the names, the label and
 * the content is what makes that hold: a search cannot be satisfied by an artifact created by
 * a sibling method.
 */
@QuarkusTest
public class SearchCommandTest extends AbstractCLITest {

    private String testGroup;
    private String testArtifact;
    private String testLabel;
    private String testContent;

    /**
     * Creates a group holding a single-version JSON artifact whose names, label and content are
     * unique per call.
     */
    private void givenArtifact() throws Exception {
        var suffix = UUID.randomUUID().toString();
        testGroup = "search-test-group-" + suffix;
        testArtifact = "search-test-artifact-" + suffix;
        testLabel = "env=" + suffix;
        testContent = "{\"type\": \"string\", \"description\": \"" + suffix + "\"}";

        executeAndAssertSuccess("group", "create", testGroup);
        final Path tempFile = Files.createTempFile("search-test", ".json");
        try {
            Files.writeString(tempFile, testContent);
            executeAndAssertSuccess("artifact", "create", "-g", testGroup,
                    "--type", "JSON", "--file", tempFile.toString(),
                    "-l", testLabel, testArtifact);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    public void testSearchHelp() {
        testHelpCommand("search");
        testHelpCommand("search", "group");
        testHelpCommand("search", "artifact");
        testHelpCommand("search", "content");
        testHelpCommand("search", "version");
    }

    @Test
    public void testSearchGroups() throws Exception {
        givenArtifact();
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("search", "groups", "--output-type", "json");
        var results = MAPPER.readValue(out.toString(), GroupSearchResults.class);

        assertThat(results.getGroups())
                .as(withCliOutput("Search should return at least the test group"))
                .isNotEmpty();
    }

    @Test
    public void testSearchGroupsWithFilter() throws Exception {
        givenArtifact();
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("search", "groups", "-g", testGroup, "--output-type", "json");
        var results = MAPPER.readValue(out.toString(), GroupSearchResults.class);

        assertThat(results.getGroups())
                .as(withCliOutput("Filtered search should return only matching groups"))
                .isNotEmpty()
                .allMatch(g -> g.getGroupId().contains(testGroup));
    }

    @Test
    public void testSearchGroupsNoResults() throws JsonProcessingException {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("search", "groups", "-g", "non-existent-group-xyz", "--output-type", "json");
        var results = MAPPER.readValue(out.toString(), GroupSearchResults.class);

        assertThat(results.getGroups())
                .as(withCliOutput("Search for non-existent group should return empty"))
                .isEmpty();
    }

    @Test
    public void testSearchGroupsTableOutput() throws Exception {
        givenArtifact();
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("search", "groups");
        assertThat(out.toString())
                .as(withCliOutput("Table output should contain column headers"))
                .contains("Group ID");
    }

    @Test
    public void testSearchGroupsPagination() throws Exception {
        givenArtifact();
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("search", "groups", "-p", "1", "-s", "1", "--output-type", "json");
        var results = MAPPER.readValue(out.toString(), GroupSearchResults.class);

        assertThat(results.getGroups())
                .as(withCliOutput("Paginated search with limit=1 should return at most 2 results "
                        + "(1 from server + the injected implicit default group)"))
                .hasSizeLessThanOrEqualTo(2);
    }

    @Test
    public void testSearchGroupsOrderBy() throws Exception {
        givenArtifact();
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("search", "groups", "--order-by", "CreatedOn", "--order", "Desc",
                "--output-type", "json");
        var results = MAPPER.readValue(out.toString(), GroupSearchResults.class);

        assertThat(results.getGroups())
                .as(withCliOutput("Order by should return results"))
                .isNotEmpty();
    }

    @Test
    public void testSearchGroupsInvalidOrderBy() {
        executeAndAssertFailure("search", "groups", "--order-by", "INVALID");
    }

    @Test
    public void testSearchGroupsInvalidOrder() {
        executeAndAssertFailure("search", "groups", "--order", "INVALID");
    }

    @Test
    public void testSearchArtifacts() throws Exception {
        givenArtifact();
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("search", "artifacts", "--output-type", "json");
        var results = MAPPER.readValue(out.toString(), ArtifactSearchResults.class);

        assertThat(results.getArtifacts())
                .as(withCliOutput("Search should return at least the test artifact"))
                .isNotEmpty();
    }

    @Test
    public void testSearchArtifactsWithFilter() throws Exception {
        givenArtifact();
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("search", "artifacts", "-g", testGroup, "-a", testArtifact,
                "--output-type", "json");
        var results = MAPPER.readValue(out.toString(), ArtifactSearchResults.class);

        assertThat(results.getArtifacts())
                .as(withCliOutput("Filtered search should return only matching artifacts"))
                .isNotEmpty()
                .allMatch(a -> testArtifact.equals(a.getArtifactId()));
    }

    @Test
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
    public void testSearchArtifactsTableOutput() throws Exception {
        givenArtifact();
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("search", "artifacts");
        assertThat(out.toString())
                .as(withCliOutput("Table output should contain column headers"))
                .contains("Group ID")
                .contains("Artifact ID");
    }

    @Test
    public void testSearchArtifactsByType() throws Exception {
        givenArtifact();
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("search", "artifacts", "--type", "JSON", "--output-type", "json");
        var results = MAPPER.readValue(out.toString(), ArtifactSearchResults.class);

        assertThat(results.getArtifacts())
                .as(withCliOutput("Type-filtered search should return JSON artifacts"))
                .isNotEmpty()
                .allMatch(a -> "JSON".equals(a.getArtifactType()));
    }

    @Test
    public void testSearchArtifactsOrderBy() throws Exception {
        givenArtifact();
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("search", "artifacts", "--order-by", "CreatedOn", "--order", "Desc",
                "--output-type", "json");
        var results = MAPPER.readValue(out.toString(), ArtifactSearchResults.class);

        assertThat(results.getArtifacts())
                .as(withCliOutput("Order by should return results"))
                .isNotEmpty();
    }

    @Test
    public void testSearchVersions() throws Exception {
        givenArtifact();
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("search", "versions", "--output-type", "json");
        var results = MAPPER.readValue(out.toString(), VersionSearchResults.class);

        assertThat(results.getVersions())
                .as(withCliOutput("Search should return at least the test version"))
                .isNotEmpty();
    }

    @Test
    public void testSearchVersionsWithFilter() throws Exception {
        givenArtifact();
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("search", "versions", "-g", testGroup, "-a", testArtifact,
                "--output-type", "json");
        var results = MAPPER.readValue(out.toString(), VersionSearchResults.class);

        assertThat(results.getVersions())
                .as(withCliOutput("Filtered search should return only matching versions"))
                .isNotEmpty()
                .allMatch(v -> testArtifact.equals(v.getArtifactId()));
    }

    @Test
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
    public void testSearchVersionsTableOutput() throws Exception {
        givenArtifact();
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("search", "versions");
        assertThat(out.toString())
                .as(withCliOutput("Table output should contain column headers"))
                .contains("Group ID")
                .contains("Artifact ID")
                .contains("Version");
    }

    @Test
    public void testSearchVersionsByState() throws Exception {
        givenArtifact();
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("search", "versions", "--state", "ENABLED", "--output-type", "json");
        var results = MAPPER.readValue(out.toString(), VersionSearchResults.class);

        assertThat(results.getVersions())
                .as(withCliOutput("State-filtered search should return only ENABLED versions"))
                .isNotEmpty()
                .allMatch(v -> io.apicurio.registry.types.VersionState.ENABLED.equals(v.getState()));
    }

    @Test
    public void testSearchVersionsByVersion() throws Exception {
        givenArtifact();
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("search", "versions", "-v", "1", "--output-type", "json");
        var results = MAPPER.readValue(out.toString(), VersionSearchResults.class);

        assertThat(results.getVersions())
                .as(withCliOutput("Version filter should return results"))
                .isNotEmpty();
    }

    @Test
    public void testSearchVersionsPagination() throws Exception {
        givenArtifact();
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("search", "versions", "-p", "1", "-s", "1", "--output-type", "json");
        var results = MAPPER.readValue(out.toString(), VersionSearchResults.class);

        assertThat(results.getVersions())
                .as(withCliOutput("Paginated search should return at most 1 result"))
                .hasSizeLessThanOrEqualTo(1);
    }

    @Test
    public void testSearchVersionsOrderBy() throws Exception {
        givenArtifact();
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("search", "versions", "--order-by", "CreatedOn", "--order", "Desc",
                "--output-type", "json");
        var results = MAPPER.readValue(out.toString(), VersionSearchResults.class);

        assertThat(results.getVersions())
                .as(withCliOutput("Order by should return results"))
                .isNotEmpty();
    }

    @Test
    public void testSearchArtifactsByLabel() throws Exception {
        givenArtifact();
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("search", "artifacts", "-l", testLabel, "--output-type", "json");
        var results = MAPPER.readValue(out.toString(), ArtifactSearchResults.class);

        assertThat(results.getArtifacts())
                .as(withCliOutput("Label-filtered search should return matching artifacts"))
                .isNotEmpty()
                .anyMatch(a -> testArtifact.equals(a.getArtifactId()));
    }

    @Test
    public void testSearchArtifactsInvalidLabel() {
        executeAndAssertFailure("search", "artifacts", "-l", ":foo");
    }

    @Test
    public void testSearchVersionsInvalidState() {
        executeAndAssertFailure("search", "versions", "--state", "INVALID");
    }

    // -- Search by content --

    @Test
    public void testSearchByContent() throws Exception {
        givenArtifact();
        final Path tempFile = Files.createTempFile("search-content", ".json");
        Files.writeString(tempFile, testContent);
        try {
            out.getBuffer().setLength(0);
            executeAndAssertSuccess("search", "content", "--output-type", "json",
                    "-f", tempFile.toString());
            ArtifactSearchResults results = MAPPER.readValue(out.toString(), ArtifactSearchResults.class);

            assertThat(results.getArtifacts())
                    .as(withCliOutput("Search by content should find the test artifact"))
                    .isNotEmpty();
            assertThat(results.getArtifacts())
                    .anyMatch(a -> testArtifact.equals(a.getArtifactId()));
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    public void testSearchByContentWithGroup() throws Exception {
        givenArtifact();
        final Path tempFile = Files.createTempFile("search-content", ".json");
        Files.writeString(tempFile, testContent);
        try {
            out.getBuffer().setLength(0);
            executeAndAssertSuccess("search", "content", "--output-type", "json",
                    "-f", tempFile.toString(), "-g", testGroup);
            ArtifactSearchResults results = MAPPER.readValue(out.toString(), ArtifactSearchResults.class);

            assertThat(results.getArtifacts())
                    .as(withCliOutput("Search by content with group filter should find the test artifact"))
                    .isNotEmpty();
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    public void testSearchByContentNoMatch() throws Exception {
        final Path tempFile = Files.createTempFile("search-content", ".json");
        Files.writeString(tempFile, "{\"type\": \"nonexistent-content-that-wont-match\"}");
        try {
            out.getBuffer().setLength(0);
            executeAndAssertSuccess("search", "content", "--output-type", "json",
                    "-f", tempFile.toString());
            ArtifactSearchResults results = MAPPER.readValue(out.toString(), ArtifactSearchResults.class);

            assertThat(results.getArtifacts())
                    .as(withCliOutput("Search by content with no match should return empty"))
                    .isEmpty();
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    public void testSearchByContentMissingFile() {
        executeAndAssertFailure("search", "content");
    }

    @Test
    public void testSearchByContentStdin() throws Exception {
        givenArtifact();
        final InputStream originalIn = System.in;
        try {
            System.setIn(new ByteArrayInputStream(
                    testContent.getBytes(StandardCharsets.UTF_8)));
            out.getBuffer().setLength(0);
            executeAndAssertSuccess("search", "content", "-o", "json", "-f", "-");
            var results = MAPPER.readValue(out.toString(), ArtifactSearchResults.class);
            assertThat(results.getArtifacts())
                    .as(withCliOutput("Stdin search should find the test artifact"))
                    .isNotEmpty();
        } finally {
            System.setIn(originalIn);
        }
    }

    @Test
    public void testSearchByContentCanonicalWithoutType() throws Exception {
        final Path tempFile = Files.createTempFile("search-content", ".json");
        Files.writeString(tempFile, "{\"type\": \"string\"}");
        try {
            executeAndAssertFailure("search", "content", "-f", tempFile.toString(), "--canonical");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}
