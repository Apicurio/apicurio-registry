package io.apicurio.registry.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import io.apicurio.registry.rest.v3.beans.Comment;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static io.apicurio.registry.cli.utils.Mapper.MAPPER;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the version comments CLI commands.
 */
@QuarkusTest
@TestMethodOrder(OrderAnnotation.class)
public class CommentCommandTest extends AbstractCLITest {

    private static final String TEST_GROUP = "comment-test-group-" + System.currentTimeMillis();
    private static final String TEST_ARTIFACT = "comment-test-artifact-" + System.currentTimeMillis();
    private static String createdCommentId;

    @Test
    @Order(0)
    public void testSetup() throws Exception {
        executeAndAssertSuccess("group", "create", TEST_GROUP);
        final Path tempFile = Files.createTempFile("comment-test", ".json");
        Files.writeString(tempFile, "{\"type\": \"string\"}");
        try {
            out.getBuffer().setLength(0);
            executeAndAssertSuccess("artifact", "create", "-g", TEST_GROUP,
                    "--type", "JSON", "--file", tempFile.toString(), TEST_ARTIFACT);
            // Verify artifact was created with a version
            out.getBuffer().setLength(0);
            executeAndAssertSuccess("artifact", "version", "-g", TEST_GROUP,
                    "-a", TEST_ARTIFACT, "--output-type", "json");
            assertThat(out.toString())
                    .as(withCliOutput("Artifact should have at least one version"))
                    .contains("versions");
        } finally {
            Files.deleteIfExists(tempFile);
        }
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
    @Order(1)
    public void testCommentListEmpty() throws JsonProcessingException {
        // When
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "version", "comment", "list",
                "-g", TEST_GROUP, "-a", TEST_ARTIFACT, "--version", "1",
                "--output-type", "json");
        var comments = MAPPER.readValue(out.toString(), new TypeReference<List<Comment>>() {
        });

        // Then
        assertThat(comments)
                .as(withCliOutput("There should not be any comments initially."))
                .isEmpty();
    }

    @Test
    @Order(2)
    public void testCommentCreateCommand() throws JsonProcessingException {
        // When
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "version", "comment", "create",
                "-g", TEST_GROUP, "-a", TEST_ARTIFACT, "--version", "1",
                "--output-type", "json",
                "-m", "This is a test comment");
        var comment = MAPPER.readValue(out.toString(), Comment.class);

        // Then
        assertThat(comment.getCommentId())
                .as(withCliOutput("Created comment should have a commentId"))
                .isNotNull()
                .isNotEmpty();
        assertThat(comment.getValue())
                .as(withCliOutput("Created comment should have the correct value"))
                .isEqualTo("This is a test comment");
        createdCommentId = comment.getCommentId();
    }

    @Test
    @Order(3)
    public void testCommentListWithComments() throws JsonProcessingException {
        // Add a second comment
        executeAndAssertSuccess("artifact", "version", "comment", "create",
                "-g", TEST_GROUP, "-a", TEST_ARTIFACT, "--version", "1",
                "-m", "Second comment");

        // When
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "version", "comment", "list",
                "-g", TEST_GROUP, "-a", TEST_ARTIFACT, "--version", "1",
                "--output-type", "json");
        var comments = MAPPER.readValue(out.toString(), new TypeReference<List<Comment>>() {
        });

        // Then
        assertThat(comments)
                .as(withCliOutput("There should be two comments."))
                .hasSize(2);
    }

    @Test
    @Order(4)
    public void testCommentUpdateCommand() {
        // When
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "version", "comment", "update",
                "-g", TEST_GROUP, "-a", TEST_ARTIFACT, "--version", "1",
                "-m", "Updated comment text",
                createdCommentId);

        // Then
        assertThat(out.toString())
                .as(withCliOutput("Update should show success message"))
                .contains("updated successfully");
    }

    @Test
    @Order(5)
    public void testCommentDeleteCommand() throws JsonProcessingException {
        // When
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "version", "comment", "delete",
                "-g", TEST_GROUP, "-a", TEST_ARTIFACT, "--version", "1",
                createdCommentId);

        // Then
        assertThat(out.toString())
                .as(withCliOutput("Delete should show success message"))
                .contains("deleted successfully");

        // Verify one comment remains
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "version", "comment", "list",
                "-g", TEST_GROUP, "-a", TEST_ARTIFACT, "--version", "1",
                "--output-type", "json");
        var comments = MAPPER.readValue(out.toString(), new TypeReference<List<Comment>>() {
        });
        assertThat(comments)
                .as(withCliOutput("There should be one comment after deletion."))
                .hasSize(1);
    }

    @Test
    public void testCommentCreateCommandFails() {
        // Missing message
        executeAndAssertFailure("artifact", "version", "comment", "create",
                "-g", TEST_GROUP, "-a", TEST_ARTIFACT, "--version", "1");
        // Missing version
        executeAndAssertFailure("artifact", "version", "comment", "create",
                "-g", TEST_GROUP, "-a", TEST_ARTIFACT, "-m", "text");
    }

    @Test
    public void testCommentUpdateCommandFails() {
        // Missing message
        executeAndAssertFailure("artifact", "version", "comment", "update",
                "-g", TEST_GROUP, "-a", TEST_ARTIFACT, "--version", "1", "some-id");
        // Missing comment ID
        executeAndAssertFailure("artifact", "version", "comment", "update",
                "-g", TEST_GROUP, "-a", TEST_ARTIFACT, "--version", "1", "-m", "text");
    }

    @Test
    public void testCommentDeleteCommandFails() {
        // Missing comment ID
        executeAndAssertFailure("artifact", "version", "comment", "delete",
                "-g", TEST_GROUP, "-a", TEST_ARTIFACT, "--version", "1");
        // Non-existent comment
        executeAndAssertFailure("artifact", "version", "comment", "delete",
                "-g", TEST_GROUP, "-a", TEST_ARTIFACT, "--version", "1", "non-existent-id");
    }

    @Test
    public void testMissingArtifactId() {
        executeAndAssertFailure("artifact", "version", "comment", "list",
                "-g", TEST_GROUP, "--version", "1");
        executeAndAssertFailure("artifact", "version", "comment", "create",
                "-g", TEST_GROUP, "--version", "1", "-m", "text");
    }

    @Test
    public void testNonExistentGroupOrArtifact() {
        // Non-existent group
        executeAndAssertFailure("artifact", "version", "comment", "list",
                "-g", "non-existent-group", "-a", TEST_ARTIFACT, "--version", "1");
        executeAndAssertFailure("artifact", "version", "comment", "create",
                "-g", "non-existent-group", "-a", TEST_ARTIFACT, "--version", "1", "-m", "text");
        // Non-existent artifact
        executeAndAssertFailure("artifact", "version", "comment", "list",
                "-g", TEST_GROUP, "-a", "non-existent-artifact", "--version", "1");
        executeAndAssertFailure("artifact", "version", "comment", "create",
                "-g", TEST_GROUP, "-a", "non-existent-artifact", "--version", "1", "-m", "text");
        // Non-existent version
        executeAndAssertFailure("artifact", "version", "comment", "list",
                "-g", TEST_GROUP, "-a", TEST_ARTIFACT, "--version", "999");
    }

    @Test
    public void testCommentTableOutput() {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "version", "comment", "list",
                "-g", TEST_GROUP, "-a", TEST_ARTIFACT, "--version", "1");
        assertThat(out.toString())
                .as(withCliOutput("Table output should contain column headers"))
                .contains("Comment ID")
                .contains("Comment")
                .contains("Owner");
    }
}
