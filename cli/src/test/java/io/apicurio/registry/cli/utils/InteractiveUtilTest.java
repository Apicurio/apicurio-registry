package io.apicurio.registry.cli.utils;

import io.apicurio.registry.rest.v3.beans.SearchedArtifact;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.Map;

import static io.apicurio.registry.cli.utils.Conversions.convertToString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InteractiveUtilTest {

    @Test
    void testPrintArtifactDetails_AllFieldsPresent() {
        var artifact = SearchedArtifact.builder()
                .groupId("test-group")
                .artifactId("test-artifact-id")
                .name("test-name")
                .artifactType("AVRO")
                .description("Test description")
                .createdOn(Date.from(Instant.parse("2026-01-01T10:00:00Z")))
                .owner("test-owner")
                .modifiedOn(Date.from(Instant.parse("2026-01-02T10:00:00Z")))
                .modifiedBy("test-modifier")
                .labels(Map.of("env", "prod", "tier", "backend"))
                .build();

        var stdout = new StringBuilder();
        InteractiveUtil.printArtifactDetails(artifact, stdout);
        String output = stdout.toString();

        assertTrue(output.contains("Group ID"));
        assertTrue(output.contains("test-group"));
        assertTrue(output.contains("Artifact ID"));
        assertTrue(output.contains("test-artifact-id"));
        assertTrue(output.contains("Name"));
        assertTrue(output.contains("test-name"));
        assertTrue(output.contains("Artifact Type"));
        assertTrue(output.contains("AVRO"));
        assertTrue(output.contains("Description"));
        assertTrue(output.contains("Test description"));
        assertTrue(output.contains("Created On"));
        assertTrue(output.contains("Owner"));
        assertTrue(output.contains("test-owner"));
        assertTrue(output.contains("Modified On"));
        assertTrue(output.contains("Modified By"));
        assertTrue(output.contains("test-modifier"));
        assertTrue(output.contains("Labels"));
        assertTrue(output.contains("env=prod"));
        assertTrue(output.contains("tier=backend"));
    }

    @Test
    void testPrintArtifactDetails_NullFieldsHandledGracefully() {
        var artifact = SearchedArtifact.builder()
                .artifactId("minimal-artifact")
                .artifactType("JSON")
                .build();

        var stdout = new StringBuilder();
        InteractiveUtil.printArtifactDetails(artifact, stdout);
        String output = stdout.toString();

        // Assert the rendered row values, not bare substrings: "minimal-artifact" also appears in
        // the Artifact ID row, so contains() alone passes even without the name fallback, and it
        // cannot catch a value landing against the wrong label.
        assertEquals("default", valueOfRow(output, "Group ID"));
        assertEquals("minimal-artifact", valueOfRow(output, "Artifact ID"));
        // null name falls back to the artifact id
        assertEquals("minimal-artifact", valueOfRow(output, "Name"));
        assertEquals("JSON", valueOfRow(output, "Artifact Type"));
        assertEquals("", valueOfRow(output, "Description"));
    }

    /**
     * Returns the value cell of a rendered "field   value" row, or null if the row is absent.
     * Splits on the column gap rather than the field name, so a field whose name prefixes another
     * cannot select the wrong row.
     */
    private static String valueOfRow(String output, String field) {
        return output.lines()
                .map(String::strip)
                .map(line -> line.split(" {2,}", 2))
                .filter(cells -> cells[0].equals(field))
                .map(cells -> cells.length > 1 ? cells[1].strip() : "")
                .findFirst()
                .orElse(null);
    }

    @Test
    void testArtifactRowRenderer() {
        var artifact = SearchedArtifact.builder()
                .artifactId("my-id")
                .name("My Artifact")
                .artifactType("AVRO")
                .createdOn(Date.from(Instant.parse("2026-01-01T10:00:00Z")))
                .build();

        // Pure function with one correct output: assert all of it, so a reordering or a dropped
        // field cannot slip through. The timestamp is formatted in the default zone, so it is
        // taken from the same helper the renderer uses rather than hardcoded.
        assertEquals("My Artifact  AVRO  " + convertToString(artifact.getCreatedOn()),
                InteractiveUtil.ARTIFACT_ROW_RENDERER.apply(artifact));
    }

    @Test
    void testArtifactRowRenderer_FallsBackToArtifactIdWhenNameIsNull() {
        var artifact = SearchedArtifact.builder()
                .artifactId("my-id")
                .artifactType("JSON")
                .createdOn(Date.from(Instant.parse("2026-01-01T10:00:00Z")))
                .build();

        assertEquals("my-id  JSON  " + convertToString(artifact.getCreatedOn()),
                InteractiveUtil.ARTIFACT_ROW_RENDERER.apply(artifact));
    }

    @Test
    void testArtifactRowSearcher() {
        var artifact = SearchedArtifact.builder()
                .groupId("my-group")
                .artifactId("my-id")
                .name("My Artifact")
                .description("Sample desc")
                .artifactType("AVRO")
                .build();

        assertEquals("my-id My Artifact my-group AVRO Sample desc",
                InteractiveUtil.ARTIFACT_ROW_SEARCHER.apply(artifact));
    }

    @Test
    void testArtifactRowSearcher_NullFieldsBecomeEmptyStrings() {
        var artifact = SearchedArtifact.builder()
                .artifactId("my-id")
                .build();

        // Every optional field is null here; the searcher must still produce a usable string.
        // The trailing separators are the four empty fields.
        assertEquals("my-id" + " ".repeat(4), InteractiveUtil.ARTIFACT_ROW_SEARCHER.apply(artifact));
    }
}
