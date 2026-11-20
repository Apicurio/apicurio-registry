package io.apicurio.registry.events.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class ArtifactStateChangeTest {

    @Test
    void testSerialization_roundTripsPreviousAndNewState() throws Exception {
        ArtifactStateChange data = new ArtifactStateChange();
        data.setGroupId("my-group");
        data.setArtifactId("my-artifact");
        data.setVersion("1");
        data.setPreviousState("ENABLED");
        data.setState("DEPRECATED");

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(data);
        ArtifactStateChange roundTripped = mapper.readValue(json, ArtifactStateChange.class);

        assertEquals("my-group", roundTripped.getGroupId());
        assertEquals("my-artifact", roundTripped.getArtifactId());
        assertEquals("1", roundTripped.getVersion());
        assertEquals("ENABLED", roundTripped.getPreviousState());
        assertEquals("DEPRECATED", roundTripped.getState());
    }

    @Test
    void testSerialization_omitsPreviousStateWhenAbsent() throws Exception {
        ArtifactStateChange data = new ArtifactStateChange();
        data.setArtifactId("my-artifact");
        data.setState("ENABLED");

        ObjectMapper mapper = new ObjectMapper();
        JsonNode json = mapper.valueToTree(data);

        assertFalse(json.has("previousState"), "previousState should be absent for a first-time state (e.g. after creation)");
        assertEquals("ENABLED", json.get("state").asText());
    }
}
