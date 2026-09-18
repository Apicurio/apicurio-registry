package io.apicurio.registry.cli;

import com.fasterxml.jackson.core.type.TypeReference;
import io.apicurio.registry.rest.v3.beans.Comment;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static io.apicurio.registry.cli.utils.Mapper.MAPPER;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the version comments CLI commands.
 *
 * <p>Every test builds the registry state it asserts on, so no execution order is declared
 * and any method can run on its own. The per-call UUID in the fixture names is what makes
 * that hold: a method cannot observe a group, artifact or comment created by a sibling, or
 * left behind by an earlier attempt at itself.
 */
@QuarkusTest
public class CommentCommandTest extends AbstractCLITest {

    private String testGroup;
    private String testArtifact;

    /**
     * Creates a group holding a single-version JSON artifact, with names unique per call.
     */
    private void givenArtifact() throws IOException {
        var suffix = UUID.randomUUID().toString();
        testGroup = "comment-test-group-" + suffix;
        testArtifact = "comment-test-artifact-" + suffix;

        executeAndAssertSuccess("group", "create", testGroup);
        final Path tempFile = Files.createTempFile("comment-test", ".json");
        try {
            Files.writeString(tempFile, "{\"type\": \"string\"}");
            executeAndAssertSuccess("artifact", "create", "-g", testGroup,
                    "--type", "JSON", "--file", tempFile.toString(), testArtifact);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    /**
     * Adds a comment to version 1 of the current fixture artifact and returns its ID.
     */
    private String givenComment(String message) throws IOException {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "version", "comment", "create",
                "-g", testGroup, "-a", testArtifact, "-v", "1",
                "--output-type", "json",
                "-m", message);
        var comment = MAPPER.readValue(out.toString(), Comment.class);
        assertThat(comment.getCommentId())
                .as(withCliOutput("Fixture comment should have a commentId"))
                .isNotEmpty();
        return comment.getCommentId();
    }

    /**
     * Lists the comments on version 1 of the current fixture artifact.
     */
    private List<Comment> listComments() throws IOException {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "version", "comment", "list",
                "-g", testGroup, "-a", testArtifact, "-v", "1",
                "--output-type", "json");
        return MAPPER.readValue(out.toString(), new TypeReference<List<Comment>>() {
        });
    }

    @Test
    public void testCommentHelp() {
        testHelpCommand("artifact", "version", "comment");
        testHelpCommand("artifact", "version", "comment", "list");
        testHelpCommand("artifact", "version", "comment", "create");
        testHelpCommand("artifact", "version", "comment", "update");
        testHelpCommand("artifact", "version", "comment", "delete");
    }

    @Test
    public void testCommentListEmpty() throws IOException {
        givenArtifact();

        // When
        var comments = listComments();

        // Then
        assertThat(comments)
                .as(withCliOutput("There should not be any comments initially."))
                .isEmpty();
    }

    @Test
    public void testCommentCreateCommand() throws IOException {
        givenArtifact();

        // When
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "version", "comment", "create",
                "-g", testGroup, "-a", testArtifact, "-v", "1",
                "--output-type", "json",
                "-m", "This is a test comment");
        var comment = MAPPER.readValue(out.toString(), Comment.class);

        // Then
        assertThat(comment.getCommentId())
                .as(withCliOutput("Created comment should have a commentId"))
                .isNotEmpty();
        assertThat(comment.getValue())
                .as(withCliOutput("Created comment should have the correct value"))
                .isEqualTo("This is a test comment");
    }

    @Test
    public void testCommentListWithComments() throws IOException {
        givenArtifact();
        givenComment("First comment");
        givenComment("Second comment");

        // When
        var comments = listComments();

        // Then
        assertThat(comments)
                .as(withCliOutput("There should be two comments."))
                .hasSize(2);
        assertThat(comments).extracting(Comment::getValue)
                .as(withCliOutput("Both comments should be listed."))
                .containsExactlyInAnyOrder("First comment", "Second comment");
    }

    @Test
    public void testCommentUpdateCommand() throws IOException {
        givenArtifact();
        var commentId = givenComment("This is a test comment");

        // When
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "version", "comment", "update",
                "-g", testGroup, "-a", testArtifact, "-v", "1",
                commentId, "-m", "Updated comment text");

        // Then
        assertThat(out.toString())
                .as(withCliOutput("Update should show success message"))
                .contains("updated successfully");
        assertThat(listComments()).extracting(Comment::getValue)
                .as(withCliOutput("The comment should hold the updated text."))
                .containsExactly("Updated comment text");
    }

    @Test
    public void testCommentDeleteCommand() throws IOException {
        givenArtifact();
        var deletedId = givenComment("Comment to delete");
        givenComment("Comment to keep");

        // When
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "version", "comment", "delete",
                "-g", testGroup, "-a", testArtifact, "-v", "1",
                deletedId);

        // Then
        assertThat(out.toString())
                .as(withCliOutput("Delete should show success message"))
                .contains("deleted successfully");
        assertThat(listComments()).extracting(Comment::getValue)
                .as(withCliOutput("Only the comment that was not deleted should remain."))
                .containsExactly("Comment to keep");
    }

    @Test
    public void testCommentCreateCommandFails() {
        // Assert on the error message so these cases cannot pass merely because the
        // command failed for some other reason, and name the argument so that one case
        // cannot pass on the other's error. Both are rejected while parsing, before the
        // command body runs, so the group and artifact need not exist.

        // Missing message
        err.getBuffer().setLength(0);
        executeAndAssertFailure("artifact", "version", "comment", "create",
                "-g", "any-group", "-a", "any-artifact", "-v", "1");
        assertThat(err.toString())
                .as(withCliOutput("The missing --message option should be reported"))
                .contains("Missing required option")
                .contains("--message");
        // Missing version
        err.getBuffer().setLength(0);
        executeAndAssertFailure("artifact", "version", "comment", "create",
                "-g", "any-group", "-a", "any-artifact", "-m", "text");
        assertThat(err.toString())
                .as(withCliOutput("The missing --version option should be reported"))
                .contains("Missing required option")
                .contains("--version");
    }

    @Test
    public void testCommentUpdateCommandFails() throws IOException {
        givenArtifact();

        // Missing message (-m). Rejected while parsing, before the command body runs.
        err.getBuffer().setLength(0);
        executeAndAssertFailure("artifact", "version", "comment", "update",
                "-g", testGroup, "-a", testArtifact, "-v", "1", "some-id");
        assertThat(err.toString())
                .as(withCliOutput("The missing --message option should be reported"))
                .contains("Missing required option")
                .contains("--message");
        // Missing comment ID (positional). Also rejected while parsing.
        err.getBuffer().setLength(0);
        executeAndAssertFailure("artifact", "version", "comment", "update",
                "-g", testGroup, "-a", testArtifact, "-v", "1", "-m", "text");
        assertThat(err.toString())
                .as(withCliOutput("The missing commentId parameter should be reported"))
                .contains("Missing required parameter")
                .contains("commentId");
        // Non-existent comment. Update is a different REST call from delete, so the
        // not-found path needs its own case here rather than riding on the delete test.
        err.getBuffer().setLength(0);
        executeAndAssertFailure("artifact", "version", "comment", "update",
                "-g", testGroup, "-a", testArtifact, "-v", "1", "non-existent-id",
                "-m", "text");
        assertThat(err.toString())
                .as(withCliOutput("Updating an unknown commentId should be reported as not found"))
                .contains("No comment with ID")
                .contains("non-existent-id");
    }

    @Test
    public void testCommentDeleteCommandFails() throws IOException {
        givenArtifact();

        // Missing comment ID
        err.getBuffer().setLength(0);
        executeAndAssertFailure("artifact", "version", "comment", "delete",
                "-g", testGroup, "-a", testArtifact, "-v", "1");
        assertThat(err.toString())
                .as(withCliOutput("The missing commentId parameter should be reported"))
                .contains("Missing required parameter")
                .contains("commentId");
        // Non-existent comment. The group and artifact are validated before the delete,
        // so naming the comment in the assertion is what proves the fixture exists and
        // that the comment itself is the only thing missing.
        err.getBuffer().setLength(0);
        executeAndAssertFailure("artifact", "version", "comment", "delete",
                "-g", testGroup, "-a", testArtifact, "-v", "1", "non-existent-id");
        assertThat(err.toString())
                .as(withCliOutput("An unknown commentId should be reported as not found"))
                .contains("No comment with ID")
                .contains("non-existent-id");
    }

    @Test
    public void testMissingArtifactId() {
        // The artifact ID is resolved before the registry client is obtained, so both
        // commands fail without contacting the server and the group need not exist.
        // resolveArtifactId falls back to the current context's artifact ID, which stays
        // unset because the test config does not enable `context.auto-update`.

        err.getBuffer().setLength(0);
        executeAndAssertFailure("artifact", "version", "comment", "list",
                "-g", "any-group", "-v", "1");
        assertThat(err.toString())
                .as(withCliOutput("Listing without an artifact ID should say so"))
                .contains("Artifact ID is required");
        err.getBuffer().setLength(0);
        executeAndAssertFailure("artifact", "version", "comment", "create",
                "-g", "any-group", "-v", "1", "-m", "text");
        assertThat(err.toString())
                .as(withCliOutput("Creating without an artifact ID should say so"))
                .contains("Artifact ID is required");
    }

    @Test
    public void testNonExistentGroupOrArtifact() throws IOException {
        givenArtifact();

        // The missing names carry a UUID as well, so that a future negative test in this
        // class cannot create one of them and silently turn these cases red for a reason
        // unrelated to the code under test.
        var missingGroup = "non-existent-group-" + UUID.randomUUID();
        var missingArtifact = "non-existent-artifact-" + UUID.randomUUID();

        // Each case asserts the message naming the entity it expects to be missing, so a
        // case cannot pass by failing for an unrelated reason such as a refused
        // connection, and cannot pass on a sibling case's error.

        // Non-existent group
        err.getBuffer().setLength(0);
        executeAndAssertFailure("artifact", "version", "comment", "list",
                "-g", missingGroup, "-a", testArtifact, "-v", "1");
        assertThat(err.toString())
                .as(withCliOutput("Listing against a missing group should name the group"))
                .contains("No group '" + missingGroup + "'");
        err.getBuffer().setLength(0);
        executeAndAssertFailure("artifact", "version", "comment", "create",
                "-g", missingGroup, "-a", testArtifact, "-v", "1", "-m", "text");
        assertThat(err.toString())
                .as(withCliOutput("Creating against a missing group should name the group"))
                .contains("No group '" + missingGroup + "'");

        // Non-existent artifact
        err.getBuffer().setLength(0);
        executeAndAssertFailure("artifact", "version", "comment", "list",
                "-g", testGroup, "-a", missingArtifact, "-v", "1");
        assertThat(err.toString())
                .as(withCliOutput("Listing against a missing artifact should name the artifact"))
                .contains("No artifact with ID '" + missingArtifact + "'");
        err.getBuffer().setLength(0);
        executeAndAssertFailure("artifact", "version", "comment", "create",
                "-g", testGroup, "-a", missingArtifact, "-v", "1", "-m", "text");
        assertThat(err.toString())
                .as(withCliOutput("Creating against a missing artifact should name the artifact"))
                .contains("No artifact with ID '" + missingArtifact + "'");

        // Non-existent version
        err.getBuffer().setLength(0);
        executeAndAssertFailure("artifact", "version", "comment", "list",
                "-g", testGroup, "-a", testArtifact, "-v", "999");
        assertThat(err.toString())
                .as(withCliOutput("Listing a missing version should name the version"))
                .contains("No version '999'");
    }

    @Test
    public void testCommentTableOutput() throws IOException {
        givenArtifact();
        var commentId = givenComment("This is a test comment");

        // When
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "version", "comment", "list",
                "-g", testGroup, "-a", testArtifact, "-v", "1");

        // Then
        var output = out.toString();
        assertThat(output)
                .as(withCliOutput("Table output should contain column headers"))
                .contains("Comment ID")
                .contains("Owner")
                .contains("Created On");
        // Headers are emitted even for an empty result set, so assert on the row itself.
        assertThat(output)
                .as(withCliOutput("Table output should render the comment that was created"))
                .contains(commentId)
                .contains("This is a test comment");
    }
}
