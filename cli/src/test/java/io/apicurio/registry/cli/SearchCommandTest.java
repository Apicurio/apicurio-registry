package io.apicurio.registry.cli;

import io.apicurio.registry.rest.v3.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.v3.beans.GroupSearchResults;
import io.apicurio.registry.rest.v3.beans.SearchedArtifact;
import io.apicurio.registry.rest.v3.beans.SearchedGroup;
import io.apicurio.registry.rest.v3.beans.SearchedVersion;
import io.apicurio.registry.rest.v3.beans.VersionSearchResults;
import io.apicurio.registry.types.VersionState;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static io.apicurio.registry.cli.utils.Mapper.MAPPER;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the search CLI commands.
 *
 * <p>No execution order is declared and any method can run on its own. Methods that assert on
 * registry data build it themselves, with a per-call UUID in every name, label and content so
 * that sibling methods in the same registry cannot match. A filter test also creates decoys that
 * the filter must exclude, because with a single fixture a filter that does nothing still passes
 * when the test runs alone.
 */
@QuarkusTest
@Timeout(120)
public class SearchCommandTest extends AbstractCLITest {

    @TempDir
    Path tempDir;

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

    /** Writes the content to a file that JUnit deletes after the test. */
    private Path contentFile(String content) throws IOException {
        return Files.writeString(tempDir.resolve(UUID.randomUUID() + ".json"), content);
    }

    /** Creates a JSON artifact from the given content; {@code extraArgs} go before the artifact id. */
    private void createJsonArtifact(String group, String artifactId, String content, String... extraArgs)
            throws IOException {
        createArtifact("JSON", group, artifactId, content, extraArgs);
    }

    private void createArtifact(String type, String group, String artifactId, String content,
            String... extraArgs) throws IOException {
        var args = new ArrayList<>(List.of("artifact", "create", "-g", group, "--type", type,
                "--file", contentFile(content).toString()));
        args.addAll(List.of(extraArgs));
        args.add(artifactId);
        executeAndAssertSuccess(args.toArray(String[]::new));
        out.getBuffer().setLength(0);
    }

    /** Adds a version whose content differs from {@code baseContent}, so it is never a duplicate. */
    private void createVersion(String group, String artifactId, String version, String baseContent)
            throws IOException {
        var content = "{\"version\": \"" + version + "\", \"base\": " + baseContent + "}";
        executeAndAssertSuccess("artifact", "version", "create", "-g", group, "-a", artifactId,
                "--file", contentFile(content).toString(), version);
        out.getBuffer().setLength(0);
    }

    /**
     * Creates two decoys for a fixture: an artifact with the same id in another group, which only a
     * group filter can exclude, and a second artifact in the same group, which only an artifact
     * filter can exclude.
     */
    private Siblings givenSiblings(Fixture f) throws IOException {
        var otherGroup = f.group() + "-other";
        var otherArtifactId = f.artifactId() + "-other";
        executeAndAssertSuccess("group", "create", otherGroup);
        createJsonArtifact(otherGroup, f.artifactId(), f.content());
        createJsonArtifact(f.group(), otherArtifactId, f.content());
        return new Siblings(otherGroup, otherArtifactId);
    }

    private record Siblings(String otherGroup, String otherArtifactId) {
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
        var group = "search-test-group-" + UUID.randomUUID();
        executeAndAssertSuccess("group", "create", group);
        executeAndAssertSuccess("group", "create", group + "-other");
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("search", "groups", "-g", group, "--output-type", "json");
        var results = MAPPER.readValue(out.toString(), GroupSearchResults.class);

        assertThat(results.getGroups())
                .as(withCliOutput("Filtered search should return only the named group"))
                .extracting(SearchedGroup::getGroupId)
                .containsExactly(group);
    }

    @Test
    public void testSearchGroupsNoResults() throws Exception {
        var group = "search-test-group-" + UUID.randomUUID();
        executeAndAssertSuccess("group", "create", group);
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("search", "groups", "-g", group + "-nope", "--output-type", "json");
        var results = MAPPER.readValue(out.toString(), GroupSearchResults.class);

        assertThat(results.getGroups())
                .as(withCliOutput("Search for non-existent group should return empty"))
                .isEmpty();
    }

    @Test
    public void testSearchGroupsTableOutput() throws Exception {
        var group = "search-test-group-" + UUID.randomUUID();
        executeAndAssertSuccess("group", "create", group);
        executeAndAssertSuccess("group", "create", group + "-other");
        out.getBuffer().setLength(0);
        // filtered, so the group is the only row and cannot fall off the default 10-row page
        executeAndAssertSuccess("search", "groups", "-g", group);
        assertThat(out.toString())
                .as(withCliOutput("Table output should contain the header and only the named group"))
                .contains("Group ID")
                .contains(group)
                .doesNotContain(group + "-other");
    }

    @Test
    public void testSearchGroupsPagination() throws Exception {
        // the label filter keeps the CLI from injecting the implicit default group, so every page
        // holds exactly the rows the server returned
        var suffix = UUID.randomUUID().toString();
        var label = "page=" + suffix;
        for (var name : List.of("a", "b", "c")) {
            executeAndAssertSuccess("group", "create", "-l", label, "search-page-" + name + "-" + suffix);
        }

        var seen = new ArrayList<String>();
        for (var page : List.of("1", "2", "3")) {
            out.getBuffer().setLength(0);
            executeAndAssertSuccess("search", "groups", "-l", label, "-p", page, "-s", "1",
                    "--output-type", "json");
            var results = MAPPER.readValue(out.toString(), GroupSearchResults.class);

            assertThat(results.getCount())
                    .as(withCliOutput("The total must count all three groups on page " + page))
                    .isEqualTo(3);
            assertThat(results.getGroups())
                    .as(withCliOutput("Page " + page + " of size 1 should hold exactly one group"))
                    .hasSize(1);
            seen.add(results.getGroups().get(0).getGroupId());
        }
        assertThat(seen)
                .as(withCliOutput("The three pages should hold the three different groups, in default order"))
                .containsExactly("search-page-a-" + suffix, "search-page-b-" + suffix,
                        "search-page-c-" + suffix);
    }

    @Test
    public void testSearchGroupsOrderBy() throws Exception {
        // two groups that share a label, so one filter finds exactly these two. The second one
        // created sorts first by name, so only CreatedOn (not the GroupId default) gives this order.
        var suffix = UUID.randomUUID().toString();
        var label = "order=" + suffix;
        var ascending = List.of("search-order-b-" + suffix, "search-order-a-" + suffix);
        var descending = List.of(ascending.get(1), ascending.get(0));
        for (var groupId : ascending) {
            executeAndAssertSuccess("group", "create", "-l", label, groupId);
        }
        // no label, so only the -l filter can leave it out
        executeAndAssertSuccess("group", "create", "search-order-unlabeled-" + suffix);

        for (var direction : List.of("Asc", "Desc")) {
            out.getBuffer().setLength(0);
            executeAndAssertSuccess("search", "groups", "--order-by", "CreatedOn", "--order", direction,
                    "-l", label, "--output-type", "json");
            var results = MAPPER.readValue(out.toString(), GroupSearchResults.class);

            assertThat(results.getGroups())
                    .as(withCliOutput("Order by CreatedOn " + direction + " should list the two groups in that order"))
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
                .contains("Invalid value for option '--order-by'")
                .contains("'INVALID'");
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
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("search", "artifacts", "--output-type", "json");
        var before = MAPPER.readValue(out.toString(), ArtifactSearchResults.class).getCount();
        givenArtifact();
        executeAndAssertSuccess("search", "artifacts", "--output-type", "json");
        var after = MAPPER.readValue(out.toString(), ArtifactSearchResults.class);

        assertThat(after.getCount())
                .as(withCliOutput("Creating one artifact should raise the unfiltered total by one"))
                .isEqualTo(before + 1);
    }

    @Test
    public void testSearchArtifactsWithFilter() throws Exception {
        var f = givenArtifact();
        givenSiblings(f);
        executeAndAssertSuccess("search", "artifacts", "-g", f.group(), "-a", f.artifactId(),
                "--output-type", "json");
        var results = MAPPER.readValue(out.toString(), ArtifactSearchResults.class);

        assertThat(results.getArtifacts())
                .as(withCliOutput("Filtered search should return only the fixture artifact"))
                .hasSize(1)
                .allMatch(a -> f.group().equals(a.getGroupId()) && f.artifactId().equals(a.getArtifactId()));
    }

    @Test
    public void testSearchArtifactsNoResults() throws Exception {
        var f = givenArtifact();
        executeAndAssertSuccess("search", "artifacts", "-g", f.group(), "-a", f.artifactId() + "-nope",
                "--output-type", "json");
        var results = MAPPER.readValue(out.toString(), ArtifactSearchResults.class);

        assertThat(results.getArtifacts())
                .as(withCliOutput("Search for non-existent artifact should return empty"))
                .isEmpty();
    }

    @Test
    public void testSearchArtifactsTableOutput() throws Exception {
        var f = givenArtifact();
        var siblings = givenSiblings(f);
        executeAndAssertSuccess("search", "artifacts", "-g", f.group(), "-a", f.artifactId());
        assertThat(out.toString())
                .as(withCliOutput("Table output should contain the header and only the fixture artifact"))
                .contains("Group ID")
                .contains("Artifact ID")
                .contains(f.group())
                .contains(f.artifactId())
                .doesNotContain(siblings.otherArtifactId())
                .doesNotContain(siblings.otherGroup());
    }

    @Test
    public void testSearchArtifactsByType() throws Exception {
        var f = givenArtifact();
        createArtifact("AVRO", f.group(), f.artifactId() + "-avro",
                "{\"type\": \"record\", \"name\": \"Decoy\", \"fields\": []}");
        executeAndAssertSuccess("search", "artifacts", "-g", f.group(), "--type", "JSON",
                "--output-type", "json");
        var results = MAPPER.readValue(out.toString(), ArtifactSearchResults.class);

        assertThat(results.getArtifacts())
                .as(withCliOutput("Type-filtered search should return the fixture JSON artifact"))
                .hasSize(1)
                .allMatch(a -> f.artifactId().equals(a.getArtifactId()) && "JSON".equals(a.getArtifactType()));
    }

    @Test
    public void testSearchArtifactsOrderBy() throws Exception {
        var f = givenArtifact();
        // created second but sorting first by id, so only CreatedOn (not the ArtifactId default)
        // yields the expected order
        var second = "a-" + f.artifactId();
        createJsonArtifact(f.group(), second, f.content());

        for (var direction : List.of("Asc", "Desc")) {
            out.getBuffer().setLength(0);
            executeAndAssertSuccess("search", "artifacts", "-g", f.group(), "--order-by", "CreatedOn",
                    "--order", direction, "--output-type", "json");
            var results = MAPPER.readValue(out.toString(), ArtifactSearchResults.class);

            assertThat(results.getArtifacts())
                    .as(withCliOutput("Order by CreatedOn " + direction + " should list the two artifacts in that order"))
                    .extracting(SearchedArtifact::getArtifactId)
                    .containsExactlyElementsOf(direction.equals("Asc")
                            ? List.of(f.artifactId(), second) : List.of(second, f.artifactId()));
        }
    }

    @Test
    public void testSearchVersions() throws Exception {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("search", "versions", "--output-type", "json");
        var before = MAPPER.readValue(out.toString(), VersionSearchResults.class).getCount();
        givenArtifact();
        executeAndAssertSuccess("search", "versions", "--output-type", "json");
        var after = MAPPER.readValue(out.toString(), VersionSearchResults.class);

        assertThat(after.getCount())
                .as(withCliOutput("Creating one artifact should raise the unfiltered version total by one"))
                .isEqualTo(before + 1);
    }

    @Test
    public void testSearchVersionsWithFilter() throws Exception {
        var f = givenArtifact();
        givenSiblings(f);
        executeAndAssertSuccess("search", "versions", "-g", f.group(), "-a", f.artifactId(),
                "--output-type", "json");
        var results = MAPPER.readValue(out.toString(), VersionSearchResults.class);

        assertThat(results.getVersions())
                .as(withCliOutput("Filtered search should return only the fixture version"))
                .hasSize(1)
                .allMatch(v -> f.group().equals(v.getGroupId()) && f.artifactId().equals(v.getArtifactId())
                        && "1".equals(v.getVersion()));
    }

    @Test
    public void testSearchVersionsNoResults() throws Exception {
        var f = givenArtifact();
        executeAndAssertSuccess("search", "versions", "-g", f.group(), "-a", f.artifactId() + "-nope",
                "--output-type", "json");
        var results = MAPPER.readValue(out.toString(), VersionSearchResults.class);

        assertThat(results.getVersions())
                .as(withCliOutput("Search for non-existent artifact versions should return empty"))
                .isEmpty();
    }

    @Test
    public void testSearchVersionsTableOutput() throws Exception {
        var f = givenArtifact();
        var siblings = givenSiblings(f);
        executeAndAssertSuccess("search", "versions", "-g", f.group(), "-a", f.artifactId());
        assertThat(out.toString())
                .as(withCliOutput("Table output should contain the header and only the fixture artifact"))
                .contains("Group ID")
                .contains("Artifact ID")
                .contains("Version")
                .contains(f.group())
                .contains(f.artifactId())
                .doesNotContain(siblings.otherArtifactId())
                .doesNotContain(siblings.otherGroup());
    }

    @Test
    public void testSearchVersionsByState() throws Exception {
        var f = givenArtifact();
        createVersion(f.group(), f.artifactId(), "2", f.content());
        executeAndAssertSuccess("artifact", "version", "update", "-g", f.group(), "-a", f.artifactId(),
                "--state", "DISABLED", "1");
        out.getBuffer().setLength(0);

        for (var state : List.of(VersionState.DISABLED, VersionState.ENABLED)) {
            out.getBuffer().setLength(0);
            executeAndAssertSuccess("search", "versions", "-g", f.group(), "-a", f.artifactId(),
                    "--state", state.name(), "--output-type", "json");
            var results = MAPPER.readValue(out.toString(), VersionSearchResults.class);

            assertThat(results.getVersions())
                    .as(withCliOutput(state + " filter should return only the version in that state"))
                    .hasSize(1)
                    .allMatch(v -> state.equals(v.getState()))
                    .extracting(SearchedVersion::getVersion)
                    .containsExactly(state == VersionState.DISABLED ? "1" : "2");
        }
    }

    @Test
    public void testSearchVersionsByVersion() throws Exception {
        var f = givenArtifact();
        createVersion(f.group(), f.artifactId(), "2", f.content());
        executeAndAssertSuccess("search", "versions", "-g", f.group(), "-a", f.artifactId(),
                "-v", "2", "--output-type", "json");
        var results = MAPPER.readValue(out.toString(), VersionSearchResults.class);

        assertThat(results.getVersions())
                .as(withCliOutput("Version filter should return only version 2 of the fixture artifact"))
                .hasSize(1)
                .allMatch(v -> "2".equals(v.getVersion()));
    }

    @Test
    public void testSearchVersionsPagination() throws Exception {
        var f = givenArtifact();
        createVersion(f.group(), f.artifactId(), "2", f.content());

        var seen = new ArrayList<String>();
        for (var page : List.of("1", "2")) {
            out.getBuffer().setLength(0);
            executeAndAssertSuccess("search", "versions", "-g", f.group(), "-a", f.artifactId(),
                    "-p", page, "-s", "1", "--output-type", "json");
            var results = MAPPER.readValue(out.toString(), VersionSearchResults.class);

            assertThat(results.getCount())
                    .as(withCliOutput("The total must count both versions on page " + page))
                    .isEqualTo(2);
            assertThat(results.getVersions())
                    .as(withCliOutput("Page " + page + " of size 1 should hold exactly one version"))
                    .hasSize(1);
            seen.add(results.getVersions().get(0).getVersion());
        }
        assertThat(seen)
                .as(withCliOutput("The two pages should hold the two different versions"))
                .containsExactly("1", "2");
    }

    @Test
    public void testSearchVersionsOrderBy() throws Exception {
        var f = givenArtifact();
        // created second but sorting first by version, so only CreatedOn (not the Version default)
        // yields the expected order
        createVersion(f.group(), f.artifactId(), "0", f.content());

        for (var direction : List.of("Asc", "Desc")) {
            out.getBuffer().setLength(0);
            executeAndAssertSuccess("search", "versions", "-g", f.group(), "-a", f.artifactId(),
                    "--order-by", "CreatedOn", "--order", direction, "--output-type", "json");
            var results = MAPPER.readValue(out.toString(), VersionSearchResults.class);

            assertThat(results.getVersions())
                    .as(withCliOutput("Order by CreatedOn " + direction + " should list the two versions in that order"))
                    .extracting(SearchedVersion::getVersion)
                    .containsExactlyElementsOf(direction.equals("Asc") ? List.of("1", "0") : List.of("0", "1"));
        }
    }

    @Test
    public void testSearchArtifactsByLabel() throws Exception {
        var first = givenArtifact();
        givenArtifact(); // decoy with a different label
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
                .contains("Invalid value for option '--state'")
                .contains("'INVALID'");
    }

    // -- Search by content --

    @Test
    public void testSearchByContent() throws Exception {
        var first = givenArtifact();
        givenArtifact(); // decoy with different content
        executeAndAssertSuccess("search", "content", "--output-type", "json",
                "-f", contentFile(first.content()).toString());
        var results = MAPPER.readValue(out.toString(), ArtifactSearchResults.class);

        assertThat(results.getArtifacts())
                .as(withCliOutput("Search by content should return only the artifact with that content"))
                .hasSize(1)
                .allMatch(a -> first.artifactId().equals(a.getArtifactId()));
    }

    @Test
    public void testSearchByContentWithGroup() throws Exception {
        var f = givenArtifact();
        // The decoy carries the SAME content in a DIFFERENT group, so only the group
        // filter can tell the two artifacts apart
        executeAndAssertSuccess("group", "create", f.group() + "-decoy");
        createJsonArtifact(f.group() + "-decoy", f.artifactId() + "-decoy", f.content());
        executeAndAssertSuccess("search", "content", "--output-type", "json",
                "-f", contentFile(f.content()).toString(), "-g", f.group());
        var results = MAPPER.readValue(out.toString(), ArtifactSearchResults.class);

        assertThat(results.getArtifacts())
                .as(withCliOutput("Search by content with group filter should return only the group's artifact"))
                .hasSize(1)
                .allMatch(a -> f.artifactId().equals(a.getArtifactId()));
    }

    @Test
    public void testSearchByContentNoMatch() throws Exception {
        var f = givenArtifact();
        var file = contentFile(f.content().replace("\"string\"", "\"number\""));
        executeAndAssertSuccess("search", "content", "--output-type", "json", "-f", file.toString());
        var results = MAPPER.readValue(out.toString(), ArtifactSearchResults.class);

        assertThat(results.getArtifacts())
                .as(withCliOutput("Search by content with no match should return empty"))
                .isEmpty();
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
        givenArtifact(); // decoy with different content
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
        var file = contentFile("{\"type\": \"string\"}");
        err.getBuffer().setLength(0);
        executeAndAssertFailure("search", "content", "-f", file.toString(), "--canonical");
        assertThat(err.toString())
                .as(withCliOutput("Canonical search without --type must be rejected"))
                .contains("--type");
    }
}
