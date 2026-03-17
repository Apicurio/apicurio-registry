package io.apicurio.registry.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.apicurio.registry.rest.v3.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v3.beans.ArtifactSearchResults;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static io.apicurio.registry.cli.utils.Mapper.MAPPER;
import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(OrderAnnotation.class)
public class ArtifactCommandTest extends AbstractCLITest {

    // -- Unordered tests (no dependency on state) --

    @Test
    public void testArtifactHelp() {
        testHelpCommand("artifact");
        testHelpCommand("artifact", "create");
        testHelpCommand("artifact", "get");
        testHelpCommand("artifact", "update");
        testHelpCommand("artifact", "delete");
    }

    @Test
    public void testArtifactCreateCommandFails() {
        // Required artifactId parameter is missing
        executeAndAssertFailure("artifact", "create", "--output-type", "json", "--group", "default");
    }

    @Test
    public void testArtifactGetContentAndOutputTypeMutuallyExclusive() {
        // --content and --output-type should not be allowed together
        executeAndAssertFailure("artifact", "get", "--content", "--output-type", "json",
                "--group", "default", "test-artifact");
    }

    @Test
    public void testArtifactGetNonExistentArtifact() {
        executeAndAssertFailure("artifact", "get", "--group", "default", "non-existent-artifact");
    }

    @Test
    public void testArtifactDeleteNonExistentArtifact() {
        executeAndAssertFailure("artifact", "delete", "--group", "default", "non-existent-artifact");
    }

    @Test
    public void testArtifactUpdateNonExistentArtifact() {
        executeAndAssertFailure("artifact", "update", "--group", "default",
                "--name", "New Name", "non-existent-artifact");
    }

    @Test
    public void testArtifactListNonExistentGroup() {
        executeAndAssertFailure("artifact", "--group", "non-existent-group");
    }

    // -- Ordered tests (depend on state from previous tests) --

    @Test
    @Order(0)
    public void testArtifactCommandEmpty() throws JsonProcessingException {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "--output-type", "json", "--group", "default");
        var artifacts = MAPPER.readValue(out.toString(), ArtifactSearchResults.class);

        assertThat(artifacts.getArtifacts())
                .as(withCliOutput("There should not be any artifacts initially."))
                .isEmpty();
    }

    @Test
    @Order(1)
    public void testArtifactCreateCommand() throws Exception {
        Path tempFile = Files.createTempFile("test-schema", ".json");
        Files.writeString(tempFile, """
                {
                  "type": "record",
                  "name": "TestRecord",
                  "fields": [
                    {"name": "id", "type": "int"},
                    {"name": "name", "type": "string"}
                  ]
                }
                """);

        try {
            out.getBuffer().setLength(0);
            executeAndAssertSuccess("artifact", "create", "--output-type", "json",
                    "--group", "default",
                    "--type", "AVRO",
                    "--name", "Test Artifact",
                    "--description", "A test artifact",
                    "--label", "env=test",
                    "--label", "color=blue",
                    "--file", tempFile.toString(),
                    "test-artifact");
            var artifact = MAPPER.readValue(out.toString(), ArtifactMetaData.class);

            assertThat(artifact.getArtifactId())
                    .as(withCliOutput("Created artifact should have the correct artifactId"))
                    .isEqualTo("test-artifact");
            assertThat(artifact.getArtifactType())
                    .as(withCliOutput("Created artifact should have the correct type"))
                    .isEqualTo("AVRO");
            assertThat(artifact.getName())
                    .as(withCliOutput("Created artifact should have the correct name"))
                    .isEqualTo("Test Artifact");
            assertThat(artifact.getDescription())
                    .as(withCliOutput("Created artifact should have the correct description"))
                    .isEqualTo("A test artifact");
            assertThat(artifact.getLabels())
                    .as(withCliOutput("Created artifact should have the correct labels"))
                    .containsExactlyInAnyOrderEntriesOf(Map.of("env", "test", "color", "blue"));
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    @Order(2)
    public void testArtifactCreateWithoutFile() throws JsonProcessingException {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "create", "--output-type", "json",
                "--group", "default",
                "--type", "JSON",
                "--name", "No Content Artifact",
                "no-content-artifact");
        var artifact = MAPPER.readValue(out.toString(), ArtifactMetaData.class);

        assertThat(artifact.getArtifactId())
                .as(withCliOutput("Created artifact should have the correct artifactId"))
                .isEqualTo("no-content-artifact");
        assertThat(artifact.getName())
                .as(withCliOutput("Created artifact should have the correct name"))
                .isEqualTo("No Content Artifact");
    }

    @Test
    @Order(3)
    public void testArtifactGetCommand() throws JsonProcessingException {
        // Get metadata
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "get", "--output-type", "json",
                "--group", "default", "test-artifact");
        var artifact = MAPPER.readValue(out.toString(), ArtifactMetaData.class);

        assertThat(artifact.getArtifactId())
                .as(withCliOutput("Retrieved artifact should have the correct artifactId"))
                .isEqualTo("test-artifact");
        assertThat(artifact.getName())
                .as(withCliOutput("Retrieved artifact should have the correct name"))
                .isEqualTo("Test Artifact");

        // Get content
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "get", "--content",
                "--group", "default", "test-artifact");
        var content = out.toString();

        assertThat(content)
                .as(withCliOutput("Content should contain the Avro schema"))
                .contains("TestRecord");
    }

    @Test
    @Order(4)
    public void testArtifactListCommand() throws Exception {
        Path tempFile = Files.createTempFile("test-schema2", ".json");
        Files.writeString(tempFile, """
                {
                  "type": "record",
                  "name": "SecondRecord",
                  "fields": [
                    {"name": "value", "type": "string"}
                  ]
                }
                """);

        try {
            executeAndAssertSuccess("artifact", "create",
                    "--group", "default",
                    "--type", "AVRO",
                    "--file", tempFile.toString(),
                    "second-artifact");

            out.getBuffer().setLength(0);
            executeAndAssertSuccess("artifact", "--output-type", "json", "--group", "default");
            var artifacts = MAPPER.readValue(out.toString(), ArtifactSearchResults.class);

            assertThat(artifacts.getArtifacts())
                    .as(withCliOutput("There should be three artifacts."))
                    .hasSize(3);

            // Test pagination
            out.getBuffer().setLength(0);
            executeAndAssertSuccess("artifact", "--output-type", "json", "--group", "default",
                    "-p", "1", "-s", "1");
            artifacts = MAPPER.readValue(out.toString(), ArtifactSearchResults.class);
            assertThat(artifacts.getArtifacts())
                    .as(withCliOutput("Page 1 with size 1 should return one artifact."))
                    .hasSize(1);
            assertThat(artifacts.getCount())
                    .as(withCliOutput("Total count should be 3."))
                    .isEqualTo(3);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    @Order(5)
    public void testArtifactUpdateCommand() throws JsonProcessingException {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "update",
                "--group", "default",
                "--name", "Updated Name",
                "--description", "Updated description",
                "--set-label", "newkey=newvalue",
                "test-artifact");

        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "get", "--output-type", "json",
                "--group", "default", "test-artifact");
        var artifact = MAPPER.readValue(out.toString(), ArtifactMetaData.class);
        assertThat(artifact.getName())
                .as(withCliOutput("Updated artifact should have the new name"))
                .isEqualTo("Updated Name");
        assertThat(artifact.getDescription())
                .as(withCliOutput("Updated artifact should have the new description"))
                .isEqualTo("Updated description");
        assertThat(artifact.getLabels())
                .as(withCliOutput("Updated artifact should have the new label"))
                .containsEntry("newkey", "newvalue");
    }

    @Test
    @Order(6)
    public void testArtifactDeleteLabel() throws JsonProcessingException {
        // Add a label
        executeAndAssertSuccess("artifact", "update", "--group", "default",
                "--set-label", "toremove=value", "test-artifact");

        // Delete the label
        executeAndAssertSuccess("artifact", "update", "--group", "default",
                "--delete-label", "toremove", "test-artifact");

        // Verify label is removed
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "get", "--output-type", "json",
                "--group", "default", "test-artifact");
        var artifact = MAPPER.readValue(out.toString(), ArtifactMetaData.class);
        assertThat(artifact.getLabels())
                .as(withCliOutput("Deleted label should no longer exist"))
                .doesNotContainKey("toremove");
    }

    @Test
    @Order(7)
    public void testArtifactDeleteCommand() throws JsonProcessingException {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "delete", "--group", "default", "second-artifact");

        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "--output-type", "json", "--group", "default");
        var artifacts = MAPPER.readValue(out.toString(), ArtifactSearchResults.class);
        assertThat(artifacts.getArtifacts())
                .as(withCliOutput("There should be two artifacts after deletion."))
                .hasSize(2);
        assertThat(artifacts.getArtifacts())
                .as(withCliOutput("Remaining artifacts should not contain second-artifact"))
                .noneMatch(a -> "second-artifact".equals(a.getArtifactId()));
    }

    @Test
    @Order(8)
    public void testArtifactWithDefaultGroupId() throws JsonProcessingException {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "--output-type", "json");
        var artifacts = MAPPER.readValue(out.toString(), ArtifactSearchResults.class);

        assertThat(artifacts.getArtifacts())
                .as(withCliOutput("Should find artifacts in default group without --group flag"))
                .isNotEmpty();
    }

    @Test
    @Order(9)
    public void testArtifactUsesGroupIdFromContext() throws Exception {
        executeAndAssertSuccess("group", "create", "context-group");
        executeAndAssertSuccess("context", "delete", "--all");
        executeAndAssertSuccess("context", "create", "test-with-group", registryUrl, "--group", "context-group");

        Path tempFile = Files.createTempFile("test-ctx-schema", ".json");
        Files.writeString(tempFile, """
                {"type": "string"}
                """);

        try {
            out.getBuffer().setLength(0);
            executeAndAssertSuccess("artifact", "create", "--type", "JSON",
                    "--file", tempFile.toString(), "context-artifact");

            out.getBuffer().setLength(0);
            executeAndAssertSuccess("artifact", "--output-type", "json");
            var artifacts = MAPPER.readValue(out.toString(), ArtifactSearchResults.class);

            assertThat(artifacts.getArtifacts())
                    .as(withCliOutput("Should find artifact in group from context"))
                    .hasSize(1);
            assertThat(artifacts.getArtifacts().get(0).getArtifactId())
                    .as(withCliOutput("Artifact should be the one created via context group"))
                    .isEqualTo("context-artifact");
            assertThat(artifacts.getArtifacts().get(0).getGroupId())
                    .as(withCliOutput("Artifact should be in 'context-group'"))
                    .isEqualTo("context-group");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}
