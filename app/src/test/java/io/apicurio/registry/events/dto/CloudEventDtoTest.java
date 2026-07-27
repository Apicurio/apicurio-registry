/*
 * Copyright 2026 The Apicurio Authors
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.apicurio.registry.events.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class CloudEventDtoTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

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
        
        CloudEventDto deserialized = objectMapper.readValue(json, CloudEventDto.class);
        assertEquals("test-id", deserialized.getId());
        assertEquals("/test-source", deserialized.getSource());
        assertEquals("io.apicurio.registry.events.TestEvent", deserialized.getType());
        assertEquals("1.0", deserialized.getSpecversion());
        assertEquals("application/json", deserialized.getDatacontenttype());
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
