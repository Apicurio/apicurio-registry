package io.apicurio.registry.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.apicurio.registry.rest.v3.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.v3.beans.GroupSearchResults;
import io.apicurio.registry.rest.v3.beans.SearchedGroup;
import io.apicurio.registry.rest.v3.beans.VersionSearchResults;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static io.apicurio.registry.cli.utils.Mapper.MAPPER;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the search CLI commands.
 *
 * <p>Every method builds the registry state it asserts on, so no execution order is declared and
 * any method can run on its own. The label and the content carry the per-call UUID too, because
 * a label or content search would otherwise also match the artifacts that sibling methods
 * created in the same registry. The label, content and stdin searches create a second fixture
 * as a decoy that must not come back, so they check that the filter narrows the result.
 */
@QuarkusTest
@Timeout(120)
public class SearchCommandTest extends AbstractCLITest {

    /**
     * Creates a group and an artifact in it, with a label and content unique to the call, and
     * returns them; the output buffer is clean when it returns.
     */
    private Fixture givenArtifact() throws IOException {
        var suffix = UUID.randomUUID().toString();
        var group = "search-test-group-" + suffix;
        var artifactId = "search-test-artifact-" + suffix;
        var label = "env=" + suffix;
        var content = "{\"type\": \"string\", \"description\": \"" + suffix + "\"}";

        executeAndAssertSuccess("group", "create", group);
        createJsonArtifact(group, artifactId, content, "-l", label);
        return new Fixture(group, artifactId, label, content);
    }

    private record Fixture(String group, String artifactId, String label, String content) {
    }

    /** Creates a JSON artifact from the given content; {@code extraArgs} go before the artifact id. */
    private void createJsonArtifact(String group, String artifactId, String content, String... extraArgs)
            throws IOException {
        final Path tempFile = Files.createTempFile("search-test", ".json");
        try {
            Files.writeString(tempFile, content);
            var args = new ArrayList<>(List.of("artifact", "create", "-g", group,
                    "--type", "JSON", "--file", tempFile.toString()));
            args.addAll(List.of(extraArgs));
            args.add(artifactId);
            executeAndAssertSuccess(args.toArray(String[]::new));
        } finally {
            Files.deleteIfExists(tempFile);
        }
        out.getBuffer().setLength(0);
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
        // an unfiltered listing is the one path that injects the implicit default group, pinned to
        // the top of the first page. The CLI adds it, so no fixture is needed.
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("search", "groups", "--output-type", "json");
        var results = MAPPER.readValue(out.toString(), GroupSearchResults.class);

        assertThat(results.getGroups())
                .as(withCliOutput("Unfiltered search should list the implicit default group first"))
                .isNotEmpty()
                .extracting(SearchedGroup::getGroupId)
                .first()
                .isEqualTo("default");
    }

    @Test
    public void testSearchGroupsWithFilter() throws Exception {
        var f = givenArtifact();
        executeAndAssertSuccess("search", "groups", "-g", f.group(), "--output-type", "json");
        var results = MAPPER.readValue(out.toString(), GroupSearchResults.class);

        assertThat(results.getGroups())
                .as(withCliOutput("Filtered search should return only matching groups"))
                .isNotEmpty()
                .allMatch(g -> g.getGroupId().contains(f.group()));
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
        var f = givenArtifact();
        // filtered, so the fixture is the only row and cannot fall off the default 10-row page
        executeAndAssertSuccess("search", "groups", "-g", f.group());
        assertThat(out.toString())
                .as(withCliOutput("Table output should contain the header and the group"))
                .contains("Group ID")
                .contains(f.group());
    }

    @Test
    public void testSearchGroupsPagination() throws Exception {
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
        // two groups that share a label, so one filter finds exactly these two
        var suffix = UUID.randomUUID().toString();
        var label = "order=" + suffix;
        var ascending = List.of("search-order-a-" + suffix, "search-order-b-" + suffix);
        for (var groupId : ascending) {
            executeAndAssertSuccess("group", "create", "-l", label, groupId);
        }
        var descending = new ArrayList<>(ascending);
        Collections.reverse(descending);

        for (var direction : List.of("Asc", "Desc")) {
            out.getBuffer().setLength(0);
            executeAndAssertSuccess("search", "groups", "--order-by", "GroupId", "--order", direction,
                    "-l", label, "--output-type", "json");
            var results = MAPPER.readValue(out.toString(), GroupSearchResults.class);

            assertThat(results.getGroups())
                    .as(withCliOutput("Order by GroupId " + direction + " should list the two groups in that order"))
                    .extracting(SearchedGroup::getGroupId)
                    .containsExactlyElementsOf(direction.equals("Asc") ? ascending : descending);
        }
    }

    @Test
    public void testSearchGroupsInvalidOrderBy() {
        err.getBuffer().setLength(0);
        executeAndAssertFailure("search", "groups", "--order-by", "INVALID");
        assertThat(err.toString())
                .as(withCliOutput("An invalid --order-by value must be reported"))
                .contains("--order-by");
    }

    @Test
    public void testSearchGroupsInvalidOrder() {
        err.getBuffer().setLength(0);
        executeAndAssertFailure("search", "groups", "--order", "INVALID");
        assertThat(err.toString())
                .as(withCliOutput("An invalid --order value must be reported"))
                .contains("Invalid value for option '--order'")
                .contains("'INVALID'");
    }

    @Test
    public void testSearchArtifacts() throws Exception {
        givenArtifact();
        executeAndAssertSuccess("search", "artifacts", "--output-type", "json");
        var results = MAPPER.readValue(out.toString(), ArtifactSearchResults.class);

        assertThat(results.getArtifacts())
                .as(withCliOutput("Search should return at least the test artifact"))
                .isNotEmpty();
    }

    @Test
    public void testSearchArtifactsWithFilter() throws Exception {
        var f = givenArtifact();
        executeAndAssertSuccess("search", "artifacts", "-g", f.group(), "-a", f.artifactId(),
                "--output-type", "json");
        var results = MAPPER.readValue(out.toString(), ArtifactSearchResults.class);

        assertThat(results.getArtifacts())
                .as(withCliOutput("Filtered search should return only matching artifacts"))
                .isNotEmpty()
                .allMatch(a -> f.artifactId().equals(a.getArtifactId()));
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
        var f = givenArtifact();
        executeAndAssertSuccess("search", "artifacts", "-g", f.group(), "-a", f.artifactId());
        assertThat(out.toString())
                .as(withCliOutput("Table output should contain the header and the artifact"))
                .contains("Group ID")
                .contains("Artifact ID")
                .contains(f.artifactId());
    }

    @Test
    public void testSearchArtifactsByType() throws Exception {
        givenArtifact();
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
        executeAndAssertSuccess("search", "versions", "--output-type", "json");
        var results = MAPPER.readValue(out.toString(), VersionSearchResults.class);

        assertThat(results.getVersions())
                .as(withCliOutput("Search should return at least the test version"))
                .isNotEmpty();
    }

    @Test
    public void testSearchVersionsWithFilter() throws Exception {
        var f = givenArtifact();
        executeAndAssertSuccess("search", "versions", "-g", f.group(), "-a", f.artifactId(),
                "--output-type", "json");
        var results = MAPPER.readValue(out.toString(), VersionSearchResults.class);

        assertThat(results.getVersions())
                .as(withCliOutput("Filtered search should return only matching versions"))
                .isNotEmpty()
                .allMatch(v -> f.artifactId().equals(v.getArtifactId()));
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
        var f = givenArtifact();
        executeAndAssertSuccess("search", "versions", "-g", f.group(), "-a", f.artifactId());
        assertThat(out.toString())
                .as(withCliOutput("Table output should contain the header and the artifact"))
                .contains("Group ID")
                .contains("Artifact ID")
                .contains("Version")
                .contains(f.artifactId());
    }

    @Test
    public void testSearchVersionsByState() throws Exception {
        givenArtifact();
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
        executeAndAssertSuccess("search", "versions", "-v", "1", "--output-type", "json");
        var results = MAPPER.readValue(out.toString(), VersionSearchResults.class);

        assertThat(results.getVersions())
                .as(withCliOutput("Version filter should return only version 1"))
                .isNotEmpty()
                .allMatch(v -> "1".equals(v.getVersion()));
    }

    @Test
    public void testSearchVersionsPagination() throws Exception {
        givenArtifact();
        executeAndAssertSuccess("search", "versions", "-p", "1", "-s", "1", "--output-type", "json");
        var results = MAPPER.readValue(out.toString(), VersionSearchResults.class);

        assertThat(results.getVersions())
                .as(withCliOutput("Paginated search should return at most 1 result"))
                .hasSizeLessThanOrEqualTo(1);
    }

    @Test
    public void testSearchVersionsOrderBy() throws Exception {
        givenArtifact();
        executeAndAssertSuccess("search", "versions", "--order-by", "CreatedOn", "--order", "Desc",
                "--output-type", "json");
        var results = MAPPER.readValue(out.toString(), VersionSearchResults.class);

        assertThat(results.getVersions())
                .as(withCliOutput("Order by should return results"))
                .isNotEmpty();
    }

    @Test
    public void testSearchArtifactsByLabel() throws Exception {
        var first = givenArtifact();
        givenArtifact();
        executeAndAssertSuccess("search", "artifacts", "-l", first.label(), "--output-type", "json");
        var results = MAPPER.readValue(out.toString(), ArtifactSearchResults.class);

        assertThat(results.getArtifacts())
                .as(withCliOutput("Label-filtered search should return only the artifact with that label"))
                .hasSize(1)
                .allMatch(a -> first.artifactId().equals(a.getArtifactId()));
    }

    @Test
    public void testSearchArtifactsInvalidLabel() {
        err.getBuffer().setLength(0);
        executeAndAssertFailure("search", "artifacts", "-l", ":foo");
        assertThat(err.toString())
                .as(withCliOutput("An invalid label value must be reported"))
                .contains(":foo");
    }

    @Test
    public void testSearchVersionsInvalidState() {
        err.getBuffer().setLength(0);
        executeAndAssertFailure("search", "versions", "--state", "INVALID");
        assertThat(err.toString())
                .as(withCliOutput("An invalid --state value must be reported"))
                .contains("--state");
    }

    // -- Search by content --

    @Test
    public void testSearchByContent() throws Exception {
        var first = givenArtifact();
        givenArtifact();
        final Path tempFile = Files.createTempFile("search-content", ".json");
        try {
            Files.writeString(tempFile, first.content());
            executeAndAssertSuccess("search", "content", "--output-type", "json",
                    "-f", tempFile.toString());
            ArtifactSearchResults results = MAPPER.readValue(out.toString(), ArtifactSearchResults.class);

            assertThat(results.getArtifacts())
                    .as(withCliOutput("Search by content should return only the artifact with that content"))
                    .hasSize(1)
                    .allMatch(a -> first.artifactId().equals(a.getArtifactId()));
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    public void testSearchByContentWithGroup() throws Exception {
        var f = givenArtifact();
        // The decoy carries the SAME content in a DIFFERENT group, so only the group
        // filter can tell the two artifacts apart
        createJsonArtifact("search-test-group-" + UUID.randomUUID(), "search-test-artifact-" + UUID.randomUUID(),
                f.content());
        final Path tempFile = Files.createTempFile("search-content", ".json");
        try {
            Files.writeString(tempFile, f.content());
            executeAndAssertSuccess("search", "content", "--output-type", "json",
                    "-f", tempFile.toString(), "-g", f.group());
            ArtifactSearchResults results = MAPPER.readValue(out.toString(), ArtifactSearchResults.class);

            assertThat(results.getArtifacts())
                    .as(withCliOutput("Search by content with group filter should return only the group's artifact"))
                    .hasSize(1)
                    .allMatch(a -> f.artifactId().equals(a.getArtifactId()));
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
        err.getBuffer().setLength(0);
        executeAndAssertFailure("search", "content");
        assertThat(err.toString())
                .as(withCliOutput("The missing --file option must be reported"))
                .contains("Missing required");
    }

    @Test
    public void testSearchByContentStdin() throws Exception {
        var first = givenArtifact();
        givenArtifact();
        final InputStream originalIn = System.in;
        try {
            System.setIn(new ByteArrayInputStream(
                    first.content().getBytes(StandardCharsets.UTF_8)));
            executeAndAssertSuccess("search", "content", "-o", "json", "-f", "-");
            var results = MAPPER.readValue(out.toString(), ArtifactSearchResults.class);
            assertThat(results.getArtifacts())
                    .as(withCliOutput("Stdin search should return only the artifact with that content"))
                    .hasSize(1)
                    .allMatch(a -> first.artifactId().equals(a.getArtifactId()));
        } finally {
            System.setIn(originalIn);
        }
    }

    @Test
    public void testSearchByContentCanonicalWithoutType() throws Exception {
        final Path tempFile = Files.createTempFile("search-content", ".json");
        try {
            Files.writeString(tempFile, "{\"type\": \"string\"}");
            err.getBuffer().setLength(0);
            executeAndAssertFailure("search", "content", "-f", tempFile.toString(), "--canonical");
            assertThat(err.toString())
                    .as(withCliOutput("Canonical search without --type must be rejected"))
                    .contains("--type");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}
