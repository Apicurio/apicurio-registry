package io.apicurio.registry.cli.utils;

import io.apicurio.registry.rest.v3.beans.SearchedArtifact;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.Map;

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

        // null groupId defaults to "default"
        assertTrue(output.contains("default"));
        // null name defaults to artifactId
        assertTrue(output.contains("minimal-artifact"));
        assertTrue(output.contains("JSON"));
    }

    @Test
    void testArtifactRowRenderer() {
        var artifact = SearchedArtifact.builder()
                .artifactId("my-id")
                .name("My Artifact")
                .artifactType("AVRO")
                .createdOn(Date.from(Instant.parse("2026-01-01T10:00:00Z")))
                .build();

        String row = InteractiveUtil.ARTIFACT_ROW_RENDERER.apply(artifact);
        assertTrue(row.contains("My Artifact"));
        assertTrue(row.contains("AVRO"));
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

        String searchText = InteractiveUtil.ARTIFACT_ROW_SEARCHER.apply(artifact);
        assertTrue(searchText.contains("my-group"));
        assertTrue(searchText.contains("my-id"));
        assertTrue(searchText.contains("My Artifact"));
        assertTrue(searchText.contains("Sample desc"));
        assertTrue(searchText.contains("AVRO"));
    }
}
