/*
 * Copyright 2026 The Apicurio Authors
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.apicurio.registry.events.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.apicurio.registry.events.ArtifactCreated;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.OutboxEvent;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CloudEventDtoTest {

    /**
     * Configured to match the production Quarkus ObjectMapper, which disables
     * WRITE_DATES_AS_TIMESTAMPS so that Instant values are written as RFC 3339
     * strings as required by the CloudEvents 1.0 specification.
     */
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    public void testBuilderPattern() {
        CloudEventDto dto = new CloudEventDto()
                .withId("test-id")
                .withSource("/test-source")
                .withType("test.type")
                .withDatacontenttype("application/json")
                .withData("{\"key\":\"value\"}")
                .withTime(Instant.now())
                .withSpecversion("1.0");

        assertEquals("test-id", dto.getId());
        assertEquals("/test-source", dto.getSource());
        assertEquals("test.type", dto.getType());
        assertEquals("application/json", dto.getDatacontenttype());
        assertEquals("{\"key\":\"value\"}", dto.getData());
        assertNotNull(dto.getTime());
        assertEquals("1.0", dto.getSpecversion());
    }

    @Test
    public void testDefaultValues() {
        CloudEventDto dto = new CloudEventDto();
        assertEquals("1.0", dto.getSpecversion());
        assertEquals("application/json", dto.getDatacontenttype());
    }

    @Test
    public void testSerialization() throws Exception {
        CloudEventDto dto = new CloudEventDto()
                .withId("test-id")
                .withSource("/test-source")
                .withType("io.apicurio.registry.events.TestEvent")
                .withData("{\"test\":\"data\"}")
                .withTime(Instant.parse("2024-01-01T00:00:00Z"));

        String json = objectMapper.writeValueAsString(dto);
        assertNotNull(json);

        // The CloudEvents 1.0 spec requires "time" to be an RFC 3339 string, not a numeric
        // epoch timestamp. Assert the wire format explicitly so a serialization regression
        // cannot hide behind a symmetric round trip.
        assertTrue(json.contains("\"time\":\"2024-01-01T00:00:00Z\""),
                "Expected time to be serialized as an RFC 3339 string, but was: " + json);

        CloudEventDto deserialized = objectMapper.readValue(json, CloudEventDto.class);
        assertEquals("test-id", deserialized.getId());
        assertEquals("/test-source", deserialized.getSource());
        assertEquals("io.apicurio.registry.events.TestEvent", deserialized.getType());
        assertEquals("1.0", deserialized.getSpecversion());
        assertEquals("application/json", deserialized.getDatacontenttype());
        assertEquals(Instant.parse("2024-01-01T00:00:00Z"), deserialized.getTime());
    }

    @Test
    public void testFromEventCreatesValidCloudEvent() {
        long createdOn = System.currentTimeMillis();

        ArtifactMetaDataDto metaDataDto = new ArtifactMetaDataDto();
        metaDataDto.setGroupId("test-group");
        metaDataDto.setArtifactId("test-artifact");
        metaDataDto.setCreatedOn(createdOn);

        ArtifactCreated event = ArtifactCreated.of(metaDataDto);
        CloudEventDto dto = CloudEventDto.from(event, "/apicurio-registry", "io.apicurio.registry.artifact.created");

        assertEquals(event.getId(), dto.getId());
        assertEquals("/apicurio-registry", dto.getSource());
        assertEquals("io.apicurio.registry.artifact.created", dto.getType());
        assertEquals("1.0", dto.getSpecversion());
        assertEquals("application/json", dto.getDatacontenttype());
        assertEquals(Instant.ofEpochMilli(createdOn), dto.getTime());
        assertNotNull(dto.getData());
    }

    @Test
    public void testFromRejectsNullEvent() {
        assertThrows(IllegalArgumentException.class,
                () -> CloudEventDto.from(null, "/apicurio-registry", "io.apicurio.registry.test.event"));
    }

    @Test
    public void testFromRejectsMissingId() {
        OutboxEvent event = new OutboxEvent(null, "test-aggregate", Instant.now()) {
            @Override
            public JSONObject getPayload() {
                return new JSONObject();
            }

            @Override
            public String getType() {
                return "ARTIFACT_CREATED";
            }
        };

        assertThrows(IllegalArgumentException.class,
                () -> CloudEventDto.from(event, "/apicurio-registry", "io.apicurio.registry.artifact.created"));
    }

    @Test
    public void testFromRejectsMissingSource() {
        OutboxEvent event = new OutboxEvent("test-id", "test-aggregate", Instant.now()) {
            @Override
            public JSONObject getPayload() {
                return new JSONObject();
            }

            @Override
            public String getType() {
                return "ARTIFACT_CREATED";
            }
        };

        assertThrows(IllegalArgumentException.class,
                () -> CloudEventDto.from(event, null, "io.apicurio.registry.artifact.created"));
    }

    @Test
    public void testFromRejectsMissingType() {
        OutboxEvent event = new OutboxEvent("test-id", "test-aggregate", Instant.now()) {
            @Override
            public JSONObject getPayload() {
                return new JSONObject();
            }

            @Override
            public String getType() {
                return "ARTIFACT_CREATED";
            }
        };

        assertThrows(IllegalArgumentException.class,
                () -> CloudEventDto.from(event, "/apicurio-registry", null));
    }

    @Test
    public void testSettersAndGetters() {
        CloudEventDto dto = new CloudEventDto();
        dto.setId("setter-id");
        dto.setSource("/setter-source");
        dto.setType("setter.type");
        dto.setDatacontenttype("text/plain");
        dto.setData("setter-data");
        dto.setTime(Instant.now());
        dto.setSpecversion("1.0");

        assertEquals("setter-id", dto.getId());
        assertEquals("/setter-source", dto.getSource());
        assertEquals("setter.type", dto.getType());
        assertEquals("text/plain", dto.getDatacontenttype());
        assertEquals("setter-data", dto.getData());
        assertNotNull(dto.getTime());
        assertEquals("1.0", dto.getSpecversion());
    }
}
