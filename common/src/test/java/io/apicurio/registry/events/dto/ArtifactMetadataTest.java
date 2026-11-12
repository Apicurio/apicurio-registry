package io.apicurio.registry.events.dto;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class ArtifactMetadataTest {

    @Test
    void testSerialization_roundTripsAllFields() throws Exception {
        ArtifactMetadata data = new ArtifactMetadata();
        data.setGroupId("my-group");
        data.setArtifactId("my-artifact");
        data.setArtifactType("AVRO");
        data.setName("My Artifact");
        data.setDescription("An example artifact");
        data.setOwner("alice");
        data.setLabels(Map.of("team", "platform"));
        data.setCreatedOn(Instant.parse("2026-01-01T00:00:00Z"));
        data.setModifiedOn(Instant.parse("2026-02-01T00:00:00Z"));

        ObjectMapper mapper = new JsonMapper();
        String json = mapper.writeValueAsString(data);
        ArtifactMetadata roundTripped = mapper.readValue(json, ArtifactMetadata.class);

        assertEquals(data.getGroupId(), roundTripped.getGroupId());
        assertEquals(data.getArtifactId(), roundTripped.getArtifactId());
        assertEquals(data.getArtifactType(), roundTripped.getArtifactType());
        assertEquals(data.getName(), roundTripped.getName());
        assertEquals(data.getDescription(), roundTripped.getDescription());
        assertEquals(data.getOwner(), roundTripped.getOwner());
        assertEquals(data.getLabels(), roundTripped.getLabels());
        assertEquals(data.getCreatedOn(), roundTripped.getCreatedOn());
        assertEquals(data.getModifiedOn(), roundTripped.getModifiedOn());
    }

    @Test
    void testSerialization_omitsNullFields() throws Exception {
        ArtifactMetadata data = new ArtifactMetadata();
        data.setGroupId("my-group");
        data.setArtifactId("my-artifact");

        ObjectMapper mapper = new JsonMapper();
        JsonNode json = mapper.valueToTree(data);

        assertFalse(json.has("description"));
        assertFalse(json.has("labels"));
        assertFalse(json.has("modifiedOn"));
    }
}
