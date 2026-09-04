package io.apicurio.registry.events.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class VersionMetadataTest {

    @Test
    void testSerialization_roundTripsAllFields() throws Exception {
        VersionMetadata data = new VersionMetadata();
        data.setGroupId("my-group");
        data.setArtifactId("my-artifact");
        data.setVersion("2");
        data.setName("v2 release");
        data.setDescription("Second version");
        data.setCreatedOn(Instant.parse("2026-03-01T00:00:00Z"));

        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        String json = mapper.writeValueAsString(data);
        VersionMetadata roundTripped = mapper.readValue(json, VersionMetadata.class);

        assertEquals("my-group", roundTripped.getGroupId());
        assertEquals("my-artifact", roundTripped.getArtifactId());
        assertEquals("2", roundTripped.getVersion());
        assertEquals("v2 release", roundTripped.getName());
        assertEquals("Second version", roundTripped.getDescription());
        assertEquals(Instant.parse("2026-03-01T00:00:00Z"), roundTripped.getCreatedOn());
    }
}
