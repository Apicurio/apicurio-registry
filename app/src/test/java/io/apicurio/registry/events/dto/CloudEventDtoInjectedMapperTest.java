/*
 * Copyright 2026 The Apicurio Authors
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.apicurio.registry.events.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.events.ArtifactCreated;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies CloudEvent serialization against the CDI-injected {@link ObjectMapper}
 * that the webhook publisher will actually use at runtime.
 * <p>
 * The plain unit test pins a hand-built mapper. This test pins the real one, so a
 * change to any {@code ObjectMapperCustomizer} that reintroduces numeric epoch
 * timestamps fails here rather than in production.
 */
@QuarkusTest
public class CloudEventDtoInjectedMapperTest {

    @Inject
    ObjectMapper objectMapper;

    @Test
    public void testTimeIsRfc3339WithInjectedMapper() throws Exception {
        CloudEventDto dto = new CloudEventDto()
                .withId("test-id")
                .withSource("/apicurio-registry")
                .withType("io.apicurio.registry.artifact.created")
                .withData("{\"test\":\"data\"}")
                .withTime(Instant.parse("2024-01-01T00:00:00Z"));

        String json = objectMapper.writeValueAsString(dto);
        assertNotNull(json);

        assertTrue(json.contains("\"time\":\"2024-01-01T00:00:00Z\""),
                "Expected time to be serialized as an RFC 3339 string by the injected ObjectMapper, but was: "
                        + json);
    }

    @Test
    public void testRoundTripWithInjectedMapper() throws Exception {
        Instant time = Instant.parse("2024-06-15T12:34:56Z");

        CloudEventDto dto = new CloudEventDto()
                .withId("round-trip-id")
                .withSource("/apicurio-registry")
                .withType("io.apicurio.registry.artifact.created")
                .withData("{\"test\":\"data\"}")
                .withTime(time);

        String json = objectMapper.writeValueAsString(dto);
        CloudEventDto deserialized = objectMapper.readValue(json, CloudEventDto.class);

        assertEquals("round-trip-id", deserialized.getId());
        assertEquals("/apicurio-registry", deserialized.getSource());
        assertEquals("io.apicurio.registry.artifact.created", deserialized.getType());
        assertEquals("1.0", deserialized.getSpecversion());
        assertEquals("application/json", deserialized.getDatacontenttype());
        assertEquals(time, deserialized.getTime());
    }

    @Test
    public void testConvertedEventSerializesWithInjectedMapper() throws Exception {
        Instant createdOn = Instant.parse("2024-03-10T08:00:00Z");

        ArtifactMetaDataDto metaDataDto = new ArtifactMetaDataDto();
        metaDataDto.setGroupId("test-group");
        metaDataDto.setArtifactId("test-artifact");
        metaDataDto.setCreatedOn(createdOn.toEpochMilli());

        ArtifactCreated event = ArtifactCreated.of(metaDataDto);

        CloudEventDto cloudEvent = CloudEventConverter.toCloudEvent(event, "/apicurio-registry");
        assertNotNull(cloudEvent);

        String json = objectMapper.writeValueAsString(cloudEvent);

        assertTrue(json.contains("\"time\":\"2024-03-10T08:00:00Z\""),
                "Expected converted event time to be the artifact createdOn as an RFC 3339 string, but was: "
                        + json);
        assertTrue(json.contains("\"specversion\":\"1.0\""),
                "Expected specversion in serialized output, but was: " + json);
    }

    /**
     * The event payload is an {@code org.json.JSONObject}. Jackson has no knowledge of that type
     * and, left alone, serializes its bean properties ({@code mapType}, {@code empty}) instead of
     * the payload, silently dropping every event field. Assert the payload fields are present on
     * the wire.
     */
    @Test
    public void testEventPayloadSurvivesSerializationWithInjectedMapper() throws Exception {
        ArtifactMetaDataDto metaDataDto = new ArtifactMetaDataDto();
        metaDataDto.setGroupId("test-group");
        metaDataDto.setArtifactId("test-artifact");
        metaDataDto.setCreatedOn(Instant.parse("2024-03-10T08:00:00Z").toEpochMilli());

        ArtifactCreated event = ArtifactCreated.of(metaDataDto);

        CloudEventDto cloudEvent = CloudEventConverter.toCloudEvent(event, "/apicurio-registry");
        assertNotNull(cloudEvent);

        String json = objectMapper.writeValueAsString(cloudEvent);
        JsonNode data = objectMapper.readTree(json).get("data");

        assertNotNull(data, "Expected a 'data' node in the serialized CloudEvent, but was: " + json);
        assertEquals("test-group", data.get("groupId").asText(),
                "Expected the event payload groupId on the wire, but was: " + json);
        assertEquals("test-artifact", data.get("artifactId").asText(),
                "Expected the event payload artifactId on the wire, but was: " + json);
        assertEquals("ARTIFACT_CREATED", data.get("eventType").asText(),
                "Expected the event payload eventType on the wire, but was: " + json);
        assertEquals(event.getId(), data.get("id").asText(),
                "Expected the event payload id on the wire, but was: " + json);
    }
}
