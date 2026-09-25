package io.apicurio.registry.storage.dto;

import org.json.JSONObject;

import java.time.Instant;

public abstract class OutboxEvent {

    private final String id;
    private final String aggregateId;
    private final Instant timestamp;

    protected OutboxEvent(String id, String aggregateId) {
        this.id = id;
        this.aggregateId = aggregateId;
        this.timestamp = Instant.now();
    }

    protected OutboxEvent(String id, String aggregateId, Instant timestamp) {
        this.id = id;
        this.aggregateId = aggregateId;
        this.timestamp = timestamp;
    }

    public String getId() {
        return this.id;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public abstract JSONObject getPayload();

    public abstract String getType();
}
