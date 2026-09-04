package io.apicurio.registry.events.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CloudEventDtoTest {

    @Test
    void testDefaults() {
        CloudEventDto event = new CloudEventDto();
        assertEquals("1.0", event.getSpecversion());
        assertEquals("application/json", event.getDatacontenttype());
    }

    @Test
    void testSerialization_includesRequiredAttributesAndOmitsAbsentOptionalOnes() throws Exception {
        CloudEventDto event = new CloudEventDto().withId("abc-123")
                .withSource("/apicurio-registry").withType(CloudEventType.ARTIFACT_CREATED.type())
                .withTime(Instant.parse("2026-01-01T00:00:00Z"));

        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        JsonNode json = mapper.valueToTree(event);

        assertEquals("1.0", json.get("specversion").asText());
        assertEquals("abc-123", json.get("id").asText());
        assertEquals("/apicurio-registry", json.get("source").asText());
        assertEquals(CloudEventType.ARTIFACT_CREATED.type(), json.get("type").asText());
        assertEquals("application/json", json.get("datacontenttype").asText());
        assertFalse(json.has("subject"), "absent optional 'subject' must not be serialized");
        assertFalse(json.has("data"), "absent optional 'data' must not be serialized");
    }

    @Test
    void testSerialization_includesSubjectAndDataWhenSet() throws Exception {
        ArtifactId subject = new ArtifactId();
        subject.setGroupId("my-group");
        subject.setArtifactId("my-artifact");

        CloudEventDto event = new CloudEventDto().withId("abc-123")
                .withSource("/apicurio-registry").withType(CloudEventType.ARTIFACT_DELETED.type())
                .withSubject("my-group/my-artifact").withData(subject);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode json = mapper.valueToTree(event);

        assertEquals("my-group/my-artifact", json.get("subject").asText());
        assertEquals("my-group", json.get("data").get("groupId").asText());
        assertEquals("my-artifact", json.get("data").get("artifactId").asText());
    }

    @Test
    void testValidate_passesWithAllRequiredAttributes() {
        CloudEventDto event = new CloudEventDto().withId("abc-123")
                .withSource("/apicurio-registry").withType(CloudEventType.ARTIFACT_UPDATED.type());
        event.validate();
    }

    @Test
    void testValidate_failsWhenIdMissing() {
        CloudEventDto event = new CloudEventDto().withSource("/apicurio-registry")
                .withType(CloudEventType.ARTIFACT_UPDATED.type());
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, event::validate);
        assertTrue(ex.getMessage().contains("id"));
    }

    @Test
    void testValidate_failsWhenSourceMissing() {
        CloudEventDto event = new CloudEventDto().withId("abc-123")
                .withType(CloudEventType.ARTIFACT_UPDATED.type());
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, event::validate);
        assertTrue(ex.getMessage().contains("source"));
    }

    @Test
    void testValidate_failsWhenTypeMissing() {
        CloudEventDto event = new CloudEventDto().withId("abc-123").withSource("/apicurio-registry");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, event::validate);
        assertTrue(ex.getMessage().contains("type"));
    }

    @Test
    void testValidate_failsWhenSpecversionBlank() {
        CloudEventDto event = new CloudEventDto().withId("abc-123").withSource("/apicurio-registry")
                .withType(CloudEventType.ARTIFACT_UPDATED.type()).withSpecversion(" ");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, event::validate);
        assertTrue(ex.getMessage().contains("specversion"));
    }
}
