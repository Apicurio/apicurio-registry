package io.apicurio.registry.storage.dto;

import com.fasterxml.jackson.databind.JsonNode;

public abstract class OutboxEvent {

    private final String id;
    private final String aggregateId;
    private final String eventType;

    protected OutboxEvent(String id, String aggregateId, String eventType) {
        this.id = id;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
    }

    public String getId() {
        return this.id;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getAggregateType() {
        return "registry-events";
    }

    public abstract JsonNode getPayload();

    public abstract String getType();
}
