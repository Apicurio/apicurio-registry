package io.apicurio.registry.support;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Test class to verify Java 8 Time serialization/deserialization with custom ObjectMapper.
 * This class is used to test the setObjectMapper() functionality added in issue #7194.
 */
public class Event {

    @JsonProperty("eventId")
    private String eventId;

    @JsonProperty("eventName")
    private String eventName;

    @JsonProperty("eventDate")
    private LocalDate eventDate;

    @JsonProperty("eventDateTime")
    private OffsetDateTime eventDateTime;

    @JsonProperty("eventTimestamp")
    private Instant eventTimestamp;

    public Event() {
    }

    public Event(String eventId, String eventName, LocalDate eventDate, OffsetDateTime eventDateTime, Instant eventTimestamp) {
        this.eventId = eventId;
        this.eventName = eventName;
        this.eventDate = eventDate;
        this.eventDateTime = eventDateTime;
        this.eventTimestamp = eventTimestamp;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public LocalDate getEventDate() {
        return eventDate;
    }

    public void setEventDate(LocalDate eventDate) {
        this.eventDate = eventDate;
    }

    public OffsetDateTime getEventDateTime() {
        return eventDateTime;
    }

    public void setEventDateTime(OffsetDateTime eventDateTime) {
        this.eventDateTime = eventDateTime;
    }

    public Instant getEventTimestamp() {
        return eventTimestamp;
    }

    public void setEventTimestamp(Instant eventTimestamp) {
        this.eventTimestamp = eventTimestamp;
    }
}
